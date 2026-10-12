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

/**
 * Báo giá (BG, BR-SAL-01..05): khách, người liên hệ, hiệu lực, điều kiện giao / bảo hành / thanh toán, nhiều phiên
 * bản. Giá dưới giá sàn hoặc chiết khấu vượt ngưỡng phải duyệt trước khi gửi khách (BR-SAL-03); không vi phạm thì tự
 * duyệt khi gửi duyệt. Gửi khách → khách chấp nhận (chuyển thành đơn bán) / từ chối / hết hạn.
 */
@Entity
@Table(name = "sales_quotes")
public class SalesQuote extends SalesDocument {

    public static final String TYPE_CODE = "BG";
    private static final EnumSet<SalesDocumentStatus> REVISABLE = EnumSet.of(SalesDocumentStatus.APPROVED,
            SalesDocumentStatus.SENT, SalesDocumentStatus.DECLINED, SalesDocumentStatus.EXPIRED);

    public record Header(LocalDate documentDate, String contactName, LocalDate validUntil, String deliveryTerms,
                         String warrantyTerms, String paymentTerms, String description) {
    }

    @Column(name = "revision_no", nullable = false, updatable = false)
    private int revisionNo = 1;

    @Column(name = "previous_quote_id", updatable = false)
    private Long previousQuoteId;

    @Column(name = "customer_id", nullable = false, updatable = false)
    private Long customerId;

    @Column(name = "contact_name")
    private String contactName;

    @Column(name = "valid_until", nullable = false)
    private LocalDate validUntil;

    @Column(name = "delivery_terms")
    private String deliveryTerms;

    @Column(name = "warranty_terms")
    private String warrantyTerms;

    @Column(name = "payment_terms")
    private String paymentTerms;

    @Column(name = "amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal amount = BigDecimal.ZERO;

    @Column(name = "discount_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal discountAmount = BigDecimal.ZERO;

    @Column(name = "tax_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal taxAmount = BigDecimal.ZERO;

    @Column(name = "total_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal totalAmount = BigDecimal.ZERO;

    @Column(name = "approval_reasons")
    private String approvalReasons;

    @Column(name = "sales_order_id")
    private Long salesOrderId;

    @Column(name = "sent_at")
    private Instant sentAt;

    @Column(name = "responded_at")
    private Instant respondedAt;

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "quote_id", nullable = false)
    @OrderBy("lineNo")
    private List<SalesQuoteLine> lines = new ArrayList<>();

    protected SalesQuote() {
    }

    public SalesQuote(Long companyId, Long branchId, String documentNo, Long customerId) {
        super(companyId, branchId, documentNo);
        this.customerId = customerId;
    }

    /** Phiên bản mới của báo giá {@code previous} (cùng khách, số phiên bản tăng). */
    public static SalesQuote revisionOf(SalesQuote previous, String documentNo) {
        SalesQuote quote = new SalesQuote(previous.getCompanyId(), previous.getBranchId(), documentNo,
                previous.customerId);
        quote.revisionNo = previous.revisionNo + 1;
        quote.previousQuoteId = previous.getId();
        quote.assignSalesUser(previous.getSalesUserId());
        return quote;
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
        return salesOrderId != null;
    }

    public void replaceContent(Header header, List<SalesQuoteLine> newLines) {
        if (newLines.isEmpty()) {
            throw BusinessException.invalidField("lines", "Báo giá phải có ít nhất một dòng.");
        }
        if (header.validUntil().isBefore(header.documentDate())) {
            throw BusinessException.invalidField("validUntil", "Hạn hiệu lực không được trước ngày báo giá.");
        }
        replaceHeader(header.documentDate(), header.description());
        this.contactName = header.contactName();
        this.validUntil = header.validUntil();
        this.deliveryTerms = header.deliveryTerms();
        this.warrantyTerms = header.warrantyTerms();
        this.paymentTerms = header.paymentTerms();
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
        amount = sum(lines.stream().map(SalesQuoteLine::getAmount).toList());
        discountAmount = sum(lines.stream().map(SalesQuoteLine::getDiscountAmount).toList());
        taxAmount = sum(lines.stream().map(SalesQuoteLine::getTaxAmount).toList());
        totalAmount = amount.add(taxAmount);
    }

    /** Lý do phải duyệt tính lúc gửi duyệt (giá dưới sàn, chiết khấu vượt ngưỡng); rỗng = không phải duyệt. */
    public void recordApprovalReasons(List<String> reasons) {
        this.approvalReasons = reasons.isEmpty() ? null : String.join(" ", reasons);
    }

    public void markSent(Instant now) {
        requireStatus(EnumSet.of(SalesDocumentStatus.APPROVED), "gửi khách");
        setStatus(SalesDocumentStatus.SENT);
        this.sentAt = now;
    }

    /** Khách chấp nhận; báo giá hết hiệu lực thì không chấp nhận được (BR-SAL-05). */
    public void accept(LocalDate today, Instant now) {
        requireStatus(EnumSet.of(SalesDocumentStatus.SENT, SalesDocumentStatus.APPROVED), "ghi nhận khách chấp nhận");
        if (validUntil.isBefore(today)) {
            throw new BusinessException(ErrorCode.INVALID_DOCUMENT_STATE, "Báo giá " + getDocumentNo()
                    + " đã hết hiệu lực ngày " + validUntil + "; lập phiên bản mới.");
        }
        setStatus(SalesDocumentStatus.ACCEPTED);
        this.respondedAt = now;
    }

    public void decline(String reason, Instant now) {
        requireStatus(EnumSet.of(SalesDocumentStatus.SENT, SalesDocumentStatus.APPROVED), "ghi nhận khách từ chối");
        setStatus(SalesDocumentStatus.DECLINED);
        setReason(reason);
        this.respondedAt = now;
    }

    /** Hết hiệu lực (tác vụ định kỳ); trả {@code true} nếu đổi trạng thái. */
    public boolean expireIfDue(LocalDate today) {
        if ((getStatus() == SalesDocumentStatus.SENT || getStatus() == SalesDocumentStatus.APPROVED)
                && validUntil.isBefore(today)) {
            setStatus(SalesDocumentStatus.EXPIRED);
            return true;
        }
        return false;
    }

    /** Thay bằng phiên bản mới: phiên bản cũ đóng lại. */
    public void supersede(String newDocumentNo, Long actorId, Instant now) {
        markClosed(REVISABLE, actorId, now, "Thay bằng phiên bản " + newDocumentNo);
    }

    /** Đã chuyển thành đơn bán (mỗi báo giá một đơn). */
    public void linkOrder(Long orderId) {
        requireStatus(EnumSet.of(SalesDocumentStatus.ACCEPTED), "chuyển thành đơn bán");
        if (salesOrderId != null) {
            throw new BusinessException(ErrorCode.INVALID_DOCUMENT_STATE, "Báo giá " + getDocumentNo()
                    + " đã chuyển thành đơn bán.");
        }
        this.salesOrderId = orderId;
    }

    private static BigDecimal sum(List<BigDecimal> values) {
        return values.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public int getRevisionNo() {
        return revisionNo;
    }

    public Long getPreviousQuoteId() {
        return previousQuoteId;
    }

    public Long getCustomerId() {
        return customerId;
    }

    public String getContactName() {
        return contactName;
    }

    public LocalDate getValidUntil() {
        return validUntil;
    }

    public String getDeliveryTerms() {
        return deliveryTerms;
    }

    public String getWarrantyTerms() {
        return warrantyTerms;
    }

    public String getPaymentTerms() {
        return paymentTerms;
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

    public String getApprovalReasons() {
        return approvalReasons;
    }

    public Long getSalesOrderId() {
        return salesOrderId;
    }

    public Instant getSentAt() {
        return sentAt;
    }

    public Instant getRespondedAt() {
        return respondedAt;
    }

    public List<SalesQuoteLine> getLines() {
        return Collections.unmodifiableList(lines);
    }
}
