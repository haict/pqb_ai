package com.company.erp.sales.domain;

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
 * Hóa đơn bán (HDB, BR-SAL-30, 31): lập từ một hoặc nhiều phiếu giao của cùng khách (và dòng dịch vụ theo đơn); hóa
 * đơn điện tử phát hành trên MISA, ERP lưu ký hiệu, số, ngày, mã cơ quan thuế (chống trùng ký hiệu + số). Ghi sổ: công
 * nợ phải thu và bút toán doanh thu (D-35); giá vốn tính ở Giai đoạn 8.
 */
@Entity
@Table(name = "sales_invoices")
public class SalesInvoice extends SalesDocument {

    public static final String TYPE_CODE = "HDB";

    public record Header(LocalDate documentDate, LocalDate postingDate, LocalDate invoiceDate, LocalDate dueDate,
                         String buyerName, String buyerTaxCode, String buyerAddress, String description) {
    }

    /** Tham chiếu hóa đơn điện tử đã phát hành trên MISA (BR-SAL-31). */
    public record EinvoiceReference(String series, String number, LocalDate date, String taxAuthorityCode) {
    }

    @Column(name = "posting_date", nullable = false)
    private LocalDate postingDate;

    @Column(name = "customer_id", nullable = false, updatable = false)
    private Long customerId;

    @Column(name = "buyer_name")
    private String buyerName;

    @Column(name = "buyer_tax_code")
    private String buyerTaxCode;

    @Column(name = "buyer_address")
    private String buyerAddress;

    @Column(name = "invoice_date", nullable = false)
    private LocalDate invoiceDate;

    @Column(name = "due_date", nullable = false)
    private LocalDate dueDate;

    @Column(name = "einvoice_series")
    private String einvoiceSeries;

    @Column(name = "einvoice_no")
    private String einvoiceNo;

    @Column(name = "einvoice_date")
    private LocalDate einvoiceDate;

    @Column(name = "tax_authority_code")
    private String taxAuthorityCode;

    @Column(name = "einvoice_key")
    private String einvoiceKey;

    @Column(name = "amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal amount = BigDecimal.ZERO;

    @Column(name = "discount_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal discountAmount = BigDecimal.ZERO;

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
    private List<SalesInvoiceLine> lines = new ArrayList<>();

    protected SalesInvoice() {
    }

    public SalesInvoice(Long companyId, Long branchId, String documentNo, Long customerId) {
        super(companyId, branchId, documentNo);
        this.customerId = customerId;
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

    public void replaceContent(Header header, List<SalesInvoiceLine> newLines) {
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
        this.invoiceDate = header.invoiceDate();
        this.dueDate = header.dueDate();
        this.buyerName = header.buyerName();
        this.buyerTaxCode = header.buyerTaxCode();
        this.buyerAddress = header.buyerAddress();
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
        amount = lines.stream().map(SalesInvoiceLine::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        discountAmount = lines.stream().map(SalesInvoiceLine::getDiscountAmount).reduce(BigDecimal.ZERO,
                BigDecimal::add);
        taxAmount = lines.stream().map(SalesInvoiceLine::getTaxAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        totalAmount = amount.add(taxAmount);
    }

    /** Ghi số hóa đơn điện tử từ MISA; được cập nhật cả sau khi ghi sổ (chưa đảo / hủy). */
    public void recordEinvoice(EinvoiceReference reference) {
        if (getStatus() == SalesDocumentStatus.CANCELLED || getStatus() == SalesDocumentStatus.REVERSED) {
            throw new BusinessException(ErrorCode.INVALID_DOCUMENT_STATE, "Hóa đơn " + getDocumentNo()
                    + " đã hủy / đảo.");
        }
        this.einvoiceSeries = reference.series().trim();
        this.einvoiceNo = reference.number().trim();
        this.einvoiceDate = reference.date();
        this.taxAuthorityCode = reference.taxAuthorityCode();
        this.einvoiceKey = einvoiceKey(einvoiceSeries, einvoiceNo);
    }

    /** Khóa chống trùng số hóa đơn điện tử: ký hiệu|số viết hoa, bỏ khoảng trắng hai đầu. */
    public static String einvoiceKey(String series, String number) {
        return (series.trim() + "|" + number.trim()).toUpperCase(Locale.ROOT);
    }

    @Override
    public void cancel(String reason) {
        super.cancel(reason);
        this.einvoiceKey = null;
    }

    public void markPosted(Long actorId, Instant now) {
        requireStatus(EnumSet.of(SalesDocumentStatus.APPROVED), "ghi sổ");
        setStatus(SalesDocumentStatus.POSTED);
        this.postedBy = actorId;
        this.postedAt = now;
    }

    public void markReversed(Long actorId, Instant now, LocalDate reverseDate, String reason) {
        requireStatus(EnumSet.of(SalesDocumentStatus.POSTED), "đảo");
        if (lines.stream().anyMatch(SalesInvoiceLine::hasDependents)) {
            throw new BusinessException(ErrorCode.HAS_DEPENDENT_DOCUMENTS, "Hóa đơn " + getDocumentNo()
                    + " đã có trả hàng / giảm giá ghi sổ; đảo các chứng từ đó trước.");
        }
        if (reverseDate.isBefore(postingDate)) {
            throw BusinessException.invalidField("postingDate", "Ngày đảo không được trước ngày ghi sổ chứng từ gốc.");
        }
        setStatus(SalesDocumentStatus.REVERSED);
        this.reversedBy = actorId;
        this.reversedAt = now;
        this.reverseDate = reverseDate;
        this.einvoiceKey = null;
        setReason(reason);
    }

    public SalesInvoiceLine line(Long lineId, String field) {
        return lines.stream().filter(candidate -> candidate.getId().equals(lineId)).findFirst()
                .orElseThrow(() -> BusinessException.invalidField(field, "Dòng không thuộc hóa đơn " + getDocumentNo()));
    }

    public void recordReturn(Long lineId, BigDecimal baseDelta, BigDecimal amountDelta, String field) {
        requirePostedFor(baseDelta.signum() > 0 || amountDelta.signum() > 0, field);
        line(lineId, field).addReturn(baseDelta, amountDelta, field);
    }

    public void recordDiscount(Long lineId, BigDecimal delta, String field) {
        requirePostedFor(delta.signum() > 0, field);
        line(lineId, field).addAdjusted(delta, field);
    }

    private void requirePostedFor(boolean increase, String field) {
        if (increase && getStatus() != SalesDocumentStatus.POSTED) {
            throw BusinessException.invalidField(field, "Hóa đơn " + getDocumentNo() + " chưa ghi sổ hoặc đã đảo.");
        }
    }

    /** Hiển thị trên công nợ: số HĐĐT nếu đã có, không thì số chứng từ. */
    public String invoiceLabel() {
        return einvoiceNo != null ? einvoiceSeries + "-" + einvoiceNo : getDocumentNo();
    }

    public LocalDate getPostingDate() {
        return postingDate;
    }

    public Long getCustomerId() {
        return customerId;
    }

    public String getBuyerName() {
        return buyerName;
    }

    public String getBuyerTaxCode() {
        return buyerTaxCode;
    }

    public String getBuyerAddress() {
        return buyerAddress;
    }

    public LocalDate getInvoiceDate() {
        return invoiceDate;
    }

    public LocalDate getDueDate() {
        return dueDate;
    }

    public String getEinvoiceSeries() {
        return einvoiceSeries;
    }

    public String getEinvoiceNo() {
        return einvoiceNo;
    }

    public LocalDate getEinvoiceDate() {
        return einvoiceDate;
    }

    public String getTaxAuthorityCode() {
        return taxAuthorityCode;
    }

    public String getEinvoiceKey() {
        return einvoiceKey;
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

    public Instant getPostedAt() {
        return postedAt;
    }

    public Instant getReversedAt() {
        return reversedAt;
    }

    public LocalDate getReverseDate() {
        return reverseDate;
    }

    public List<SalesInvoiceLine> getLines() {
        return Collections.unmodifiableList(lines);
    }
}
