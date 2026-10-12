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
import java.util.Locale;

/**
 * Hóa đơn mua (HDM, BR-PUR-09): chứng từ thuế của nhà cung cấp, tham chiếu một hoặc nhiều phiếu nhận hàng (và một phiếu
 * nhận có thể vào nhiều hóa đơn). Chống trùng theo (MST người bán, ký hiệu, số). Ghi sổ: công nợ phải trả và bút toán
 * (D-34), chênh lệch giá so với giá tạm tính lưu theo dòng phiếu nhận (BR-PUR-10). Đảo hoàn tất cả.
 */
@Entity
@Table(name = "purchase_invoices")
public class PurchaseInvoice extends PurchaseDocument {

    public static final String TYPE_CODE = "HDM";

    public record Header(LocalDate documentDate, LocalDate postingDate, String sellerTaxCode, String invoiceSeries,
                         String invoiceNo, LocalDate invoiceDate, LocalDate dueDate, String description) {
    }

    @Column(name = "posting_date", nullable = false)
    private LocalDate postingDate;

    @Column(name = "supplier_id", nullable = false, updatable = false)
    private Long supplierId;

    @Column(name = "seller_tax_code", nullable = false)
    private String sellerTaxCode;

    @Column(name = "invoice_series", nullable = false)
    private String invoiceSeries;

    @Column(name = "invoice_no", nullable = false)
    private String invoiceNo;

    @Column(name = "invoice_date", nullable = false)
    private LocalDate invoiceDate;

    @Column(name = "due_date", nullable = false)
    private LocalDate dueDate;

    @Column(name = "duplicate_key")
    private String duplicateKey;

    @Column(name = "amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal amount = BigDecimal.ZERO;

    @Column(name = "tax_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal taxAmount = BigDecimal.ZERO;

    @Column(name = "total_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal totalAmount = BigDecimal.ZERO;

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
    @JoinColumn(name = "invoice_id", nullable = false)
    @OrderBy("lineNo")
    private List<PurchaseInvoiceLine> lines = new ArrayList<>();

    protected PurchaseInvoice() {
    }

    public PurchaseInvoice(Long companyId, Long branchId, String documentNo, Long supplierId) {
        super(companyId, branchId, documentNo);
        this.supplierId = supplierId;
    }

    /** Khóa chống trùng: MST, ký hiệu, số không phân biệt hoa thường, bỏ khoảng trắng hai đầu. */
    public static String duplicateKey(String sellerTaxCode, String invoiceSeries, String invoiceNo) {
        return (sellerTaxCode.trim() + "|" + invoiceSeries.trim() + "|" + invoiceNo.trim()).toUpperCase(Locale.ROOT);
    }

    @Override
    public String typeCode() {
        return TYPE_CODE;
    }

    @Override
    public BigDecimal approvalAmount() {
        return totalAmount;
    }

    @Override
    protected boolean fulfilled() {
        return false;
    }

    @Override
    protected boolean hasProgress() {
        return false;
    }

    public void replaceContent(Header header, List<PurchaseInvoiceLine> newLines) {
        if (newLines.isEmpty()) {
            throw BusinessException.invalidField("lines", "Hóa đơn phải có ít nhất một dòng.");
        }
        if (header.postingDate().isBefore(header.documentDate())) {
            throw BusinessException.invalidField("postingDate", "Ngày hạch toán không được trước ngày chứng từ.");
        }
        if (header.dueDate().isBefore(header.invoiceDate())) {
            throw BusinessException.invalidField("dueDate", "Hạn thanh toán không được trước ngày hóa đơn.");
        }
        replaceHeader(header.documentDate(), header.description());
        this.postingDate = header.postingDate();
        this.sellerTaxCode = header.sellerTaxCode().trim();
        this.invoiceSeries = header.invoiceSeries().trim();
        this.invoiceNo = header.invoiceNo().trim();
        this.invoiceDate = header.invoiceDate();
        this.dueDate = header.dueDate();
        this.duplicateKey = duplicateKey(sellerTaxCode, invoiceSeries, invoiceNo);
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
        amount = lines.stream().map(PurchaseInvoiceLine::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        taxAmount = lines.stream().map(PurchaseInvoiceLine::getTaxAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        totalAmount = amount.add(taxAmount);
    }

    /** Hủy giải phóng khóa chống trùng để lập lại hóa đơn đúng. */
    @Override
    public void cancel(String reason) {
        super.cancel(reason);
        this.duplicateKey = null;
    }

    public void markPosted(Long actorId, Instant now) {
        requireStatus(EnumSet.of(PurchaseDocumentStatus.APPROVED), "ghi sổ");
        setStatus(PurchaseDocumentStatus.POSTED);
        this.postedBy = actorId;
        this.postedAt = now;
    }

    public void markReversed(Long actorId, Instant now, LocalDate reverseDate, String reason) {
        requireStatus(EnumSet.of(PurchaseDocumentStatus.POSTED), "đảo");
        if (reverseDate.isBefore(postingDate)) {
            throw BusinessException.invalidField("postingDate", "Ngày đảo không được trước ngày ghi sổ chứng từ gốc.");
        }
        if (lines.stream().anyMatch(PurchaseInvoiceLine::hasDependents)) {
            throw new BusinessException(ErrorCode.HAS_DEPENDENT_DOCUMENTS, "Hóa đơn " + getDocumentNo()
                    + " đã có trả hàng / giảm giá / phân bổ chi phí ghi sổ; đảo các chứng từ đó trước.");
        }
        setStatus(PurchaseDocumentStatus.REVERSED);
        this.reversedBy = actorId;
        this.reversedAt = now;
        this.reverseDate = reverseDate;
        this.duplicateKey = null;
        setReason(reason);
    }

    /** Trả hàng theo dòng hóa đơn (hóa đơn phải đang ghi sổ). */
    public void recordReturn(Long lineId, BigDecimal baseDelta, BigDecimal amountDelta, String field) {
        requirePostedFor(baseDelta.signum() > 0 || amountDelta.signum() > 0, field);
        line(lineId, field).addReturn(baseDelta, amountDelta, field);
    }

    /** Giảm giá hàng mua theo dòng hóa đơn. */
    public void recordDiscount(Long lineId, BigDecimal delta, String field) {
        requirePostedFor(delta.signum() > 0, field);
        line(lineId, field).addAdjusted(delta, field);
    }

    /** Phân bổ chi phí mua từ dòng dịch vụ của hóa đơn. */
    public void recordAllocation(Long lineId, BigDecimal delta, String field) {
        requirePostedFor(delta.signum() > 0, field);
        line(lineId, field).addAllocated(delta, field);
    }

    public PurchaseInvoiceLine line(Long lineId, String field) {
        return lines.stream().filter(candidate -> candidate.getId().equals(lineId)).findFirst()
                .orElseThrow(() -> BusinessException.invalidField(field, "Dòng không thuộc hóa đơn " + getDocumentNo()));
    }

    private void requirePostedFor(boolean increase, String field) {
        if (increase && getStatus() != PurchaseDocumentStatus.POSTED) {
            throw BusinessException.invalidField(field, "Hóa đơn " + getDocumentNo() + " chưa ghi sổ hoặc đã đảo.");
        }
    }

    /** Hiển thị trên công nợ: ký hiệu và số hóa đơn. */
    public String invoiceLabel() {
        return invoiceSeries + "-" + invoiceNo;
    }

    public LocalDate getPostingDate() {
        return postingDate;
    }

    public Long getSupplierId() {
        return supplierId;
    }

    public String getSellerTaxCode() {
        return sellerTaxCode;
    }

    public String getInvoiceSeries() {
        return invoiceSeries;
    }

    public String getInvoiceNo() {
        return invoiceNo;
    }

    public LocalDate getInvoiceDate() {
        return invoiceDate;
    }

    public LocalDate getDueDate() {
        return dueDate;
    }

    public String getDuplicateKey() {
        return duplicateKey;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public BigDecimal getTaxAmount() {
        return taxAmount;
    }

    public BigDecimal getTotalAmount() {
        return totalAmount;
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

    public List<PurchaseInvoiceLine> getLines() {
        return Collections.unmodifiableList(lines);
    }
}
