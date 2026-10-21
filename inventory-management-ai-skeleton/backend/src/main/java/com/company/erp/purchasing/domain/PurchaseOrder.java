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
 * Đơn mua hàng (DMH, BR-PUR-04, D-54): chứng từ ghi sổ duy nhất của nghiệp vụ mua. Nhà cung cấp, kho nhập, hóa đơn
 * của nhà cung cấp (không bắt buộc; có thì chống trùng theo MST, ký hiệu, số), dòng hàng có giá, chiết khấu, thuế, lô /
 * hạn dùng / serial. Duyệt xong cộng hàng đang về; ghi sổ nhập kho, công nợ phải trả, bút toán và trừ hàng đang về;
 * đảo hoàn tất cả (BR mục 5.3, 5.4).
 */
@Entity
@Table(name = "purchase_orders")
public class PurchaseOrder extends PurchaseDocument {

    public static final String TYPE_CODE = "DMH";

    /** Hóa đơn của nhà cung cấp gắn trên đơn mua. */
    public record Invoice(String sellerTaxCode, String series, String number, LocalDate date) {
    }

    /** Header gửi khi lập / sửa; {@code invoice} {@code null} khi mua không có hóa đơn. */
    public record Header(LocalDate documentDate, LocalDate postingDate, Long supplierId, Long warehouseId,
                         LocalDate expectedDate, Integer paymentTermDays, String currencyCode, BigDecimal exchangeRate,
                         Invoice invoice, LocalDate dueDate, String description) {
    }

    @Column(name = "posting_date", nullable = false)
    private LocalDate postingDate;

    @Column(name = "supplier_id", nullable = false)
    private Long supplierId;

    @Column(name = "warehouse_id", nullable = false)
    private Long warehouseId;

    @Column(name = "expected_date")
    private LocalDate expectedDate;

    @Column(name = "payment_term_days")
    private Integer paymentTermDays;

    @Column(name = "currency_code", nullable = false)
    private String currencyCode;

    @Column(name = "exchange_rate", nullable = false, precision = 19, scale = 6)
    private BigDecimal exchangeRate;

    @Column(name = "seller_tax_code")
    private String sellerTaxCode;

    @Column(name = "invoice_series")
    private String invoiceSeries;

    @Column(name = "invoice_no")
    private String invoiceNo;

    @Column(name = "invoice_date")
    private LocalDate invoiceDate;

    @Column(name = "due_date")
    private LocalDate dueDate;

    @Column(name = "duplicate_key")
    private String duplicateKey;

    @Column(name = "amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal amount = BigDecimal.ZERO;

    @Column(name = "discount_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal discountAmount = BigDecimal.ZERO;

    @Column(name = "tax_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal taxAmount = BigDecimal.ZERO;

    @Column(name = "total_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal totalAmount = BigDecimal.ZERO;

    @Column(name = "inventory_document_id")
    private Long inventoryDocumentId;

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
    @JoinColumn(name = "order_id", nullable = false)
    @OrderBy("lineNo")
    private List<PurchaseOrderLine> lines = new ArrayList<>();

    protected PurchaseOrder() {
    }

    public PurchaseOrder(Long companyId, Long branchId, String documentNo) {
        super(companyId, branchId, documentNo);
    }

    /** Khóa chống trùng hóa đơn: MST, ký hiệu, số không phân biệt hoa thường, bỏ khoảng trắng hai đầu. */
    public static String duplicateKey(String sellerTaxCode, String invoiceSeries, String invoiceNo) {
        return (sellerTaxCode.trim() + "|" + invoiceSeries.trim() + "|" + invoiceNo.trim()).toUpperCase(Locale.ROOT);
    }

    @Override
    public String typeCode() {
        return TYPE_CODE;
    }


    /** Thay nội dung (chỉ DRAFT); tổng tiền tính lại từ dòng. */
    public void replaceContent(Header header, List<PurchaseOrderLine> newLines) {
        if (newLines.isEmpty()) {
            throw BusinessException.invalidField("lines", "Đơn mua phải có ít nhất một dòng.");
        }
        if (header.postingDate().isBefore(header.documentDate())) {
            throw BusinessException.invalidField("postingDate", "Ngày hạch toán không được trước ngày chứng từ.");
        }
        replaceHeader(header.documentDate(), header.description());
        this.postingDate = header.postingDate();
        this.supplierId = header.supplierId();
        this.warehouseId = header.warehouseId();
        this.expectedDate = header.expectedDate();
        this.paymentTermDays = header.paymentTermDays();
        this.currencyCode = header.currencyCode();
        this.exchangeRate = header.exchangeRate();
        this.dueDate = header.dueDate();
        Invoice invoice = header.invoice();
        this.sellerTaxCode = invoice == null ? null : invoice.sellerTaxCode().trim();
        this.invoiceSeries = invoice == null ? null : invoice.series().trim();
        this.invoiceNo = invoice == null ? null : invoice.number().trim();
        this.invoiceDate = invoice == null ? null : invoice.date();
        this.duplicateKey = invoice == null ? null : duplicateKey(sellerTaxCode, invoiceSeries, invoiceNo);
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
        amount = lines.stream().map(PurchaseOrderLine::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        discountAmount = lines.stream().map(PurchaseOrderLine::getDiscountAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        taxAmount = lines.stream().map(PurchaseOrderLine::getTaxAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        totalAmount = amount.subtract(discountAmount).add(taxAmount);
    }

    /** Hủy giải phóng khóa chống trùng hóa đơn để lập lại đơn đúng. */
    @Override
    public void cancel(String reason) {
        super.cancel(reason);
        this.duplicateKey = null;
    }

    /** Ghi sổ (từ DRAFT, hoặc APPROVED trước D-61); {@code inventoryDocumentId} trống khi đơn chỉ có dịch vụ. */
    public void markPosted(Long inventoryDocumentId, Long actorId, Instant now) {
        requireStatus(POSTABLE, "ghi sổ");
        setStatus(PurchaseDocumentStatus.POSTED);
        this.inventoryDocumentId = inventoryDocumentId;
        this.postedBy = actorId;
        this.postedAt = now;
    }

    /** Đảo: chặn khi còn trả hàng / giảm giá / phân bổ chi phí chưa đảo (BR-GEN-04). */
    public void markReversed(Long actorId, Instant now, LocalDate reverseDate, String reason) {
        requireStatus(EnumSet.of(PurchaseDocumentStatus.POSTED), "đảo");
        if (reverseDate.isBefore(postingDate)) {
            throw BusinessException.invalidField("postingDate", "Ngày đảo không được trước ngày ghi sổ chứng từ gốc.");
        }
        requireNoDependents();
        setStatus(PurchaseDocumentStatus.REVERSED);
        this.reversedBy = actorId;
        this.reversedAt = now;
        this.reverseDate = reverseDate;
        this.duplicateKey = null;
        setReason(reason);
    }

    /**
     * Bỏ ghi sổ về nháp, giữ số đơn và khóa chống trùng hóa đơn (D-62); sổ phụ đảo ở tầng application. Chặn như đảo khi
     * còn trả hàng / giảm giá / phân bổ chi phí chưa đảo.
     */
    public void markUnposted(String reason) {
        requireStatus(EnumSet.of(PurchaseDocumentStatus.POSTED), "bỏ ghi sổ");
        requireNoDependents();
        setStatus(PurchaseDocumentStatus.DRAFT);
        this.inventoryDocumentId = null;
        this.postedBy = null;
        this.postedAt = null;
        setReason(reason);
    }

    private void requireNoDependents() {
        if (lines.stream().anyMatch(PurchaseOrderLine::hasDependents)) {
            throw new BusinessException(ErrorCode.HAS_DEPENDENT_DOCUMENTS, "Đơn mua " + getDocumentNo()
                    + " đã có trả hàng / giảm giá / phân bổ chi phí ghi sổ; đảo các chứng từ đó trước.");
        }
    }

    /** Trả hàng theo dòng (đơn phải đang ghi sổ). */
    public void recordReturn(Long lineId, BigDecimal baseDelta, BigDecimal amountDelta, String field) {
        requirePostedFor(baseDelta.signum() > 0 || amountDelta.signum() > 0, field);
        line(lineId, field).addReturn(baseDelta, amountDelta, field);
    }

    /** Giảm giá hàng mua theo dòng. */
    public void recordDiscount(Long lineId, BigDecimal delta, String field) {
        requirePostedFor(delta.signum() > 0, field);
        line(lineId, field).addAdjusted(delta, field);
    }

    /** Phân bổ chi phí mua từ dòng dịch vụ. */
    public void recordAllocation(Long lineId, BigDecimal delta, String field) {
        requirePostedFor(delta.signum() > 0, field);
        line(lineId, field).addAllocated(delta, field);
    }

    public PurchaseOrderLine line(Long lineId, String field) {
        return lines.stream().filter(candidate -> candidate.getId().equals(lineId)).findFirst()
                .orElseThrow(() -> BusinessException.invalidField(field, "Dòng không thuộc đơn mua "
                        + getDocumentNo() + "."));
    }

    private void requirePostedFor(boolean increase, String field) {
        if (increase && getStatus() != PurchaseDocumentStatus.POSTED) {
            throw BusinessException.invalidField(field, "Đơn mua " + getDocumentNo() + " chưa ghi sổ hoặc đã đảo.");
        }
    }

    public boolean hasInvoice() {
        return invoiceNo != null;
    }

    /** Hiển thị trên công nợ: ký hiệu và số hóa đơn, hoặc số đơn mua khi không có hóa đơn. */
    public String invoiceLabel() {
        return hasInvoice() ? invoiceSeries + "-" + invoiceNo : getDocumentNo();
    }

    public boolean hasGoods() {
        return lines.stream().anyMatch(line -> !line.isServiceLine());
    }

    public LocalDate getPostingDate() {
        return postingDate;
    }

    public Long getSupplierId() {
        return supplierId;
    }

    public Long getWarehouseId() {
        return warehouseId;
    }

    public LocalDate getExpectedDate() {
        return expectedDate;
    }

    public Integer getPaymentTermDays() {
        return paymentTermDays;
    }

    public String getCurrencyCode() {
        return currencyCode;
    }

    public BigDecimal getExchangeRate() {
        return exchangeRate;
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

    public BigDecimal getDiscountAmount() {
        return discountAmount;
    }

    public BigDecimal getTaxAmount() {
        return taxAmount;
    }

    public BigDecimal getTotalAmount() {
        return totalAmount;
    }

    public Long getInventoryDocumentId() {
        return inventoryDocumentId;
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

    public List<PurchaseOrderLine> getLines() {
        return Collections.unmodifiableList(lines);
    }
}
