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
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Đơn bán hàng (DBH, BR-SAL-10..15): cam kết bán, chứng từ điều phối nhóm B. Giá dưới sàn, chiết khấu vượt ngưỡng,
 * vượt hạn mức tín dụng hoặc có nợ quá hạn thì phải duyệt (BR-SAL-03, BR-SAL-14, D-22); không vi phạm thì tự duyệt.
 * Giữ hàng theo đơn khi chọn (BR-SAL-13, D-37). Tiến độ theo dòng: xuất kho, giao hàng, hóa đơn, trả lại (BR-SAL-20).
 */
@Entity
@Table(name = "sales_orders")
public class SalesOrder extends SalesDocument {

    public static final String TYPE_CODE = "DBH";

    public record Header(LocalDate documentDate, String contactName, String deliveryAddress, String receiverName,
                         String receiverPhone, LocalDate expectedDate, Integer paymentTermDays, boolean reserveStock,
                         String description) {
    }

    /** Đợt thu tiền khai báo khi lập (số tiền tính theo tỷ lệ của tổng đơn). */
    public record PaymentTermValues(BigDecimal percent, PaymentDueBasis dueBasis, int dueDays, String description) {
    }

    @Column(name = "customer_id", nullable = false, updatable = false)
    private Long customerId;

    @Column(name = "quote_id", updatable = false)
    private Long quoteId;

    @Column(name = "contact_name")
    private String contactName;

    @Column(name = "delivery_address")
    private String deliveryAddress;

    @Column(name = "receiver_name")
    private String receiverName;

    @Column(name = "receiver_phone")
    private String receiverPhone;

    @Column(name = "warehouse_id", nullable = false)
    private Long warehouseId;

    @Column(name = "expected_date")
    private LocalDate expectedDate;

    @Column(name = "payment_term_days")
    private Integer paymentTermDays;

    @Column(name = "reserve_stock", nullable = false)
    private boolean reserveStock;

    @Column(name = "reserved_until")
    private Instant reservedUntil;

    @Column(name = "currency_code", nullable = false, updatable = false)
    private String currencyCode = "VND";

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

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "order_id", nullable = false)
    @OrderBy("lineNo")
    private List<SalesOrderLine> lines = new ArrayList<>();

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "order_id", nullable = false)
    @OrderBy("installmentNo")
    private List<SalesOrderPaymentTerm> paymentTerms = new ArrayList<>();

    protected SalesOrder() {
    }

    public SalesOrder(Long companyId, Long branchId, String documentNo, Long customerId, Long quoteId) {
        super(companyId, branchId, documentNo);
        this.customerId = customerId;
        this.quoteId = quoteId;
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
        return lines.stream().allMatch(SalesOrderLine::fulfilled);
    }

    @Override
    protected boolean hasProgress() {
        return lines.stream().anyMatch(SalesOrderLine::hasProgress);
    }

    public void replaceContent(Header header, Long warehouseId, List<SalesOrderLine> newLines,
                               List<PaymentTermValues> terms) {
        if (newLines.isEmpty()) {
            throw BusinessException.invalidField("lines", "Đơn bán phải có ít nhất một dòng.");
        }
        replaceHeader(header.documentDate(), header.description());
        this.contactName = header.contactName();
        this.deliveryAddress = header.deliveryAddress();
        this.receiverName = header.receiverName();
        this.receiverPhone = header.receiverPhone();
        this.expectedDate = header.expectedDate();
        this.paymentTermDays = header.paymentTermDays();
        this.reserveStock = header.reserveStock();
        this.warehouseId = warehouseId;
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
        amount = sum(lines.stream().map(SalesOrderLine::getAmount).toList());
        discountAmount = sum(lines.stream().map(SalesOrderLine::getDiscountAmount).toList());
        taxAmount = sum(lines.stream().map(SalesOrderLine::getTaxAmount).toList());
        totalAmount = amount.add(taxAmount);
        replaceTerms(terms);
    }

    /** Lịch thu tiền: tổng tỷ lệ 100%; số tiền đợt cuối nhận phần dư làm tròn (BR-SAL-50). */
    private void replaceTerms(List<PaymentTermValues> terms) {
        if (!terms.isEmpty()) {
            BigDecimal percent = terms.stream().map(PaymentTermValues::percent).reduce(BigDecimal.ZERO,
                    BigDecimal::add);
            if (percent.compareTo(BigDecimal.valueOf(100)) != 0) {
                throw BusinessException.invalidField("paymentTerms", "Tổng tỷ lệ các đợt thu tiền phải bằng 100%.");
            }
        }
        BigDecimal allocated = BigDecimal.ZERO;
        for (int i = 0; i < terms.size(); i++) {
            PaymentTermValues term = terms.get(i);
            BigDecimal value = i == terms.size() - 1 ? totalAmount.subtract(allocated)
                    : totalAmount.multiply(term.percent()).divide(BigDecimal.valueOf(100), 0, RoundingMode.HALF_UP);
            allocated = allocated.add(value);
            if (i < paymentTerms.size()) {
                paymentTerms.get(i).apply(term.percent(), value, term.dueBasis(), term.dueDays(), term.description());
            } else {
                paymentTerms.add(new SalesOrderPaymentTerm(i + 1, term.percent(), value, term.dueBasis(),
                        term.dueDays(), term.description()));
            }
        }
        while (paymentTerms.size() > terms.size()) {
            paymentTerms.remove(paymentTerms.size() - 1);
        }
    }

    public void recordApprovalReasons(List<String> reasons) {
        this.approvalReasons = reasons.isEmpty() ? null : String.join(" ", reasons);
    }

    public void markReserved(Instant until) {
        this.reservedUntil = until;
    }

    /** Đơn đã duyệt còn mở (lập phiếu giao, giữ / nhả hàng). */
    public void requireOpen() {
        if (!isOpen()) {
            throw new BusinessException(ErrorCode.INVALID_DOCUMENT_STATE, "Đơn bán " + getDocumentNo()
                    + " chưa duyệt hoặc đã hoàn thành / đóng / hủy.");
        }
    }

    public SalesOrderLine line(Long lineId) {
        return lines.stream().filter(line -> line.getId().equals(lineId)).findFirst()
                .orElseThrow(() -> new IllegalStateException("Dòng #" + lineId + " không thuộc đơn " + getDocumentNo()));
    }

    /** Xuất kho theo dòng (dương: kiểm tra đơn còn mở, không vượt số còn phải xuất; âm: đảo). */
    public void recordIssue(Long lineId, BigDecimal delta, String field) {
        if (delta.signum() > 0) {
            requireOpen();
        }
        line(lineId).addIssued(delta, field);
        refreshProgress();
    }

    public void recordDelivery(Long lineId, BigDecimal delta) {
        line(lineId).addDelivered(delta);
        refreshProgress();
    }

    public void recordInvoice(Long lineId, BigDecimal delta) {
        line(lineId).addInvoiced(delta);
        refreshProgress();
    }

    public void recordReturn(Long lineId, BigDecimal delta) {
        line(lineId).addReturned(delta);
        if (getStatus() == SalesDocumentStatus.COMPLETED || isOpen()) {
            refreshProgress();
        }
    }

    /** Đóng phần còn lại có lý do (BR-SAL-20). */
    public void close(Long actorId, Instant now, String reason) {
        lines.forEach(SalesOrderLine::closeRemaining);
        markClosed(actorId, now, reason);
    }

    /** Dòng hàng hóa còn phải xuất (giữ hàng, đề nghị mua). */
    public List<SalesOrderLine> openGoodsLines() {
        return lines.stream().filter(line -> !line.isServiceLine() && line.outstandingQuantity().signum() > 0)
                .toList();
    }

    private static BigDecimal sum(List<BigDecimal> values) {
        return values.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public Long getCustomerId() {
        return customerId;
    }

    public Long getQuoteId() {
        return quoteId;
    }

    public String getContactName() {
        return contactName;
    }

    public String getDeliveryAddress() {
        return deliveryAddress;
    }

    public String getReceiverName() {
        return receiverName;
    }

    public String getReceiverPhone() {
        return receiverPhone;
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

    public boolean isReserveStock() {
        return reserveStock;
    }

    public Instant getReservedUntil() {
        return reservedUntil;
    }

    public String getCurrencyCode() {
        return currencyCode;
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

    public List<SalesOrderLine> getLines() {
        return Collections.unmodifiableList(lines);
    }

    public List<SalesOrderPaymentTerm> getPaymentTerms() {
        return Collections.unmodifiableList(paymentTerms);
    }
}
