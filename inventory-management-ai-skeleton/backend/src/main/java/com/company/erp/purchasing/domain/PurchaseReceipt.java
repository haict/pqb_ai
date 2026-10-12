package com.company.erp.purchasing.domain;

import com.company.erp.common.domain.BusinessException;
import com.company.erp.common.domain.ErrorCode;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;

/**
 * Phiếu nhận hàng (PNH, BR-PUR-07): ghi nhận hàng thực tế đến theo đơn mua, kiểm đếm, hàng lỗi; không đổi tồn. Ghi sổ
 * (POSTED) cập nhật số đã nhận / lỗi của đơn mua; nhập kho lập phiếu nhập kho PNK riêng ở module kho (D-36). Chỉ phải
 * duyệt khi nhận vượt số đặt quá dung sai. Đảo hoàn cả phiếu nhập kho.
 */
@Entity
@Table(name = "purchase_receipts")
public class PurchaseReceipt extends PurchaseDocument {

    public static final String TYPE_CODE = "PNH";

    /** Đầu phiếu khi lập / sửa; nhà cung cấp, kho lấy theo đơn mua. */
    public record Header(LocalDate documentDate, LocalDate postingDate, String deliveryNoteNo, String description) {
    }

    @Column(name = "posting_date", nullable = false)
    private LocalDate postingDate;

    @Column(name = "purchase_order_id", nullable = false, updatable = false)
    private Long purchaseOrderId;

    @Column(name = "supplier_id", nullable = false, updatable = false)
    private Long supplierId;

    @Column(name = "warehouse_id", nullable = false, updatable = false)
    private Long warehouseId;

    @Column(name = "delivery_note_no")
    private String deliveryNoteNo;

    @Column(name = "over_tolerance", nullable = false)
    private boolean overTolerance;

    @Column(name = "inventory_document_id")
    private Long inventoryDocumentId;

    @Column(name = "stocked_by")
    private Long stockedBy;

    @Column(name = "stocked_at")
    private Instant stockedAt;

    @Column(name = "posted_by")
    private Long postedBy;

    @Column(name = "posted_at")
    private Instant postedAt;

    @Column(name = "reversed_by")
    private Long reversedBy;

    @Column(name = "reversed_at")
    private Instant reversedAt;

    @Column(name = "reverse_date")
    private LocalDate reverseDate;

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "receipt_id", nullable = false)
    @OrderBy("lineNo")
    private List<PurchaseReceiptLine> lines = new ArrayList<>();

    protected PurchaseReceipt() {
    }

    public PurchaseReceipt(Long companyId, Long branchId, String documentNo, Long purchaseOrderId, Long supplierId,
                           Long warehouseId) {
        super(companyId, branchId, documentNo);
        this.purchaseOrderId = purchaseOrderId;
        this.supplierId = supplierId;
        this.warehouseId = warehouseId;
    }

    @Override
    public String typeCode() {
        return TYPE_CODE;
    }

    @Override
    public BigDecimal approvalAmount() {
        return null;
    }

    @Override
    protected boolean fulfilled() {
        return false;
    }

    @Override
    protected boolean hasProgress() {
        return false;
    }

    public void replaceContent(Header header, List<PurchaseReceiptLine> newLines) {
        if (newLines.isEmpty()) {
            throw BusinessException.invalidField("lines", "Phiếu nhận hàng phải có ít nhất một dòng.");
        }
        if (header.postingDate().isBefore(header.documentDate())) {
            throw BusinessException.invalidField("postingDate", "Ngày hạch toán không được trước ngày chứng từ.");
        }
        replaceHeader(header.documentDate(), header.description());
        this.postingDate = header.postingDate();
        this.deliveryNoteNo = header.deliveryNoteNo();
        for (int i = 0; i < newLines.size(); i++) {
            if (i < lines.size()) {
                lines.get(i).copyFrom(newLines.get(i));
            } else {
                lines.add(newLines.get(i));
            }
        }
        while (lines.size() > newLines.size()) {
            lines.remove(lines.size() - 1);
        }
    }

    /** Ghi lại kết quả xét dung sai lúc gửi duyệt (vượt dung sai thì đã qua luồng duyệt). */
    public void markOverTolerance(boolean overTolerance) {
        this.overTolerance = overTolerance;
    }

    /** Xác nhận nhận hàng (chỉ chứng từ đã duyệt). */
    public void markPosted(Long actorId, Instant now) {
        requireStatus(EnumSet.of(PurchaseDocumentStatus.APPROVED), "ghi sổ");
        setStatus(PurchaseDocumentStatus.POSTED);
        this.postedBy = actorId;
        this.postedAt = now;
    }

    /** Đã nhập kho; {@code inventoryDocumentId} trống khi mọi dòng trả lại NCC. */
    public void markStocked(Long inventoryDocumentId, Long actorId, Instant now) {
        requireStatus(EnumSet.of(PurchaseDocumentStatus.POSTED), "nhập kho");
        if (isStocked()) {
            throw new BusinessException(ErrorCode.INVALID_DOCUMENT_STATE, "Phiếu " + getDocumentNo()
                    + " đã nhập kho.");
        }
        this.inventoryDocumentId = inventoryDocumentId;
        this.stockedBy = actorId;
        this.stockedAt = now;
    }

    public void markReversed(Long actorId, Instant now, LocalDate reverseDate, String reason) {
        requireStatus(EnumSet.of(PurchaseDocumentStatus.POSTED), "đảo");
        if (hasInvoices()) {
            throw new BusinessException(ErrorCode.HAS_DEPENDENT_DOCUMENTS, "Phiếu " + getDocumentNo()
                    + " đã có hóa đơn mua ghi sổ; đảo hóa đơn trước.");
        }
        if (lines.stream().anyMatch(line -> line.getReturnedQuantity().signum() > 0)) {
            throw new BusinessException(ErrorCode.HAS_DEPENDENT_DOCUMENTS, "Phiếu " + getDocumentNo()
                    + " đã có trả hàng nhà cung cấp ghi sổ; đảo phiếu trả hàng trước.");
        }
        if (reverseDate.isBefore(postingDate)) {
            throw BusinessException.invalidField("postingDate", "Ngày đảo không được trước ngày ghi sổ chứng từ gốc.");
        }
        setStatus(PurchaseDocumentStatus.REVERSED);
        this.reversedBy = actorId;
        this.reversedAt = now;
        this.reverseDate = reverseDate;
        setReason(reason);
    }

    /** Hóa đơn mua theo dòng đã nhập kho; phiếu phải đã ghi sổ và nhập kho, chưa đảo. */
    public void recordInvoice(Long lineId, BigDecimal delta, String field) {
        if (delta.signum() > 0 && (getStatus() != PurchaseDocumentStatus.POSTED || !isStocked())) {
            throw BusinessException.invalidField(field, "Phiếu nhận hàng " + getDocumentNo()
                    + " chưa nhập kho hoặc đã đảo.");
        }
        PurchaseReceiptLine line = lines.stream().filter(candidate -> candidate.getId().equals(lineId)).findFirst()
                .orElseThrow(() -> BusinessException.invalidField(field, "Dòng không thuộc phiếu " + getDocumentNo()));
        line.addInvoiced(delta, field);
    }

    /** Trả hàng nhà cung cấp theo dòng đã nhập kho; phiếu phải đã ghi sổ, nhập kho, chưa đảo (BR-PUR-14). */
    public void recordReturn(Long lineId, BigDecimal delta, boolean invoiced, String field) {
        if (delta.signum() > 0 && (getStatus() != PurchaseDocumentStatus.POSTED || !isStocked())) {
            throw BusinessException.invalidField(field, "Phiếu nhận hàng " + getDocumentNo()
                    + " chưa nhập kho hoặc đã đảo.");
        }
        line(lineId, field).addReturned(delta, invoiced, field);
    }

    public PurchaseReceiptLine line(Long lineId, String field) {
        return lines.stream().filter(candidate -> candidate.getId().equals(lineId)).findFirst()
                .orElseThrow(() -> BusinessException.invalidField(field, "Dòng không thuộc phiếu " + getDocumentNo()));
    }

    /** Đã có hóa đơn mua ghi sổ theo phiếu (đảo phiếu phải đảo hóa đơn trước, BR-GEN-22). */
    public boolean hasInvoices() {
        return lines.stream().anyMatch(line -> line.getInvoicedQuantity().signum() > 0);
    }

    public boolean isStocked() {
        return stockedAt != null;
    }

    public LocalDate getPostingDate() {
        return postingDate;
    }

    public Long getPurchaseOrderId() {
        return purchaseOrderId;
    }

    public Long getSupplierId() {
        return supplierId;
    }

    public Long getWarehouseId() {
        return warehouseId;
    }

    public String getDeliveryNoteNo() {
        return deliveryNoteNo;
    }

    public boolean isOverTolerance() {
        return overTolerance;
    }

    public Long getInventoryDocumentId() {
        return inventoryDocumentId;
    }

    public Instant getStockedAt() {
        return stockedAt;
    }

    public Instant getPostedAt() {
        return postedAt;
    }

    public Instant getReversedAt() {
        return reversedAt;
    }

    public LocalDate getReverseDate() {
        return reverseDate;
    }

    public List<PurchaseReceiptLine> getLines() {
        return Collections.unmodifiableList(lines);
    }
}
