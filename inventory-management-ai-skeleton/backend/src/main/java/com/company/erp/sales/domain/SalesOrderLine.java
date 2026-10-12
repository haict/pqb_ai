package com.company.erp.sales.domain;

import com.company.erp.common.domain.BusinessException;
import com.company.erp.common.domain.ErrorCode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Dòng đơn bán và tiến độ theo đơn vị cơ sở (BR-SAL-20): đã xuất, đã giao, đã xuất hóa đơn, đã trả lại, đã đóng. Còn
 * phải xuất = đặt − đóng − (đã xuất − đã trả lại): hàng khách trả lại mở lại phần cần giao (giao bù / đổi hàng); không
 * giao bù thì đóng phần còn lại `[Giả định]`. Dòng dịch vụ không qua kho: tiến độ theo số đã xuất hóa đơn.
 */
@Entity
@Table(name = "sales_order_lines")
public class SalesOrderLine extends SalesPricedLine {

    @Column(name = "quote_line_id")
    private Long quoteLineId;

    @Column(name = "service_line", nullable = false)
    private boolean serviceLine;

    @Column(name = "expected_date")
    private LocalDate expectedDate;

    @Column(name = "issued_quantity", nullable = false, precision = 19, scale = 3)
    private BigDecimal issuedQuantity = BigDecimal.ZERO;

    @Column(name = "delivered_quantity", nullable = false, precision = 19, scale = 3)
    private BigDecimal deliveredQuantity = BigDecimal.ZERO;

    @Column(name = "invoiced_quantity", nullable = false, precision = 19, scale = 3)
    private BigDecimal invoicedQuantity = BigDecimal.ZERO;

    @Column(name = "returned_quantity", nullable = false, precision = 19, scale = 3)
    private BigDecimal returnedQuantity = BigDecimal.ZERO;

    @Column(name = "closed_quantity", nullable = false, precision = 19, scale = 3)
    private BigDecimal closedQuantity = BigDecimal.ZERO;

    protected SalesOrderLine() {
    }

    public record Values(PricedValues price, Long quoteLineId, boolean serviceLine, LocalDate expectedDate) {
    }

    public SalesOrderLine(int lineNo, Values values) {
        super(lineNo);
        apply(values);
    }

    private void apply(Values values) {
        applyPrice(values.price());
        this.quoteLineId = values.quoteLineId();
        this.serviceLine = values.serviceLine();
        this.expectedDate = values.expectedDate();
    }

    void copyFrom(SalesOrderLine other) {
        apply(new Values(other.priceValues(), other.quoteLineId, other.serviceLine, other.expectedDate));
    }

    /** Còn phải xuất kho (hàng hóa) / còn phải xuất hóa đơn (dịch vụ). */
    public BigDecimal outstandingQuantity() {
        BigDecimal done = serviceLine ? invoicedQuantity : issuedQuantity.subtract(returnedQuantity);
        return getBaseQuantity().subtract(closedQuantity).subtract(done).max(BigDecimal.ZERO);
    }

    /** Đã giao đủ (hàng hóa, trừ phần khách trả lại) / đã xuất hóa đơn đủ (dịch vụ). */
    boolean fulfilled() {
        BigDecimal done = serviceLine ? invoicedQuantity : deliveredQuantity.subtract(returnedQuantity);
        return done.compareTo(getBaseQuantity().subtract(closedQuantity)) >= 0;
    }

    boolean hasProgress() {
        return issuedQuantity.signum() > 0 || invoicedQuantity.signum() > 0;
    }

    /** Xuất kho theo đơn (dương) / đảo (âm); không vượt số còn phải xuất (BR-SAL-20). */
    void addIssued(BigDecimal delta, String field) {
        if (delta.signum() > 0 && delta.compareTo(outstandingQuantity()) > 0) {
            throw new BusinessException(ErrorCode.SOURCE_QUANTITY_EXCEEDED, field + ": dòng " + getLineNo()
                    + " của đơn bán chỉ còn " + plain(outstandingQuantity()) + " (đơn vị cơ sở) chưa xuất.");
        }
        issuedQuantity = issuedQuantity.add(delta).max(BigDecimal.ZERO);
    }

    void addDelivered(BigDecimal delta) {
        deliveredQuantity = deliveredQuantity.add(delta).max(BigDecimal.ZERO);
    }

    void addInvoiced(BigDecimal delta) {
        invoicedQuantity = invoicedQuantity.add(delta).max(BigDecimal.ZERO);
    }

    void addReturned(BigDecimal delta) {
        returnedQuantity = returnedQuantity.add(delta).max(BigDecimal.ZERO);
    }

    void closeRemaining() {
        closedQuantity = closedQuantity.add(outstandingQuantity());
    }

    private static String plain(BigDecimal value) {
        return value.stripTrailingZeros().toPlainString();
    }

    public Long getQuoteLineId() {
        return quoteLineId;
    }

    public boolean isServiceLine() {
        return serviceLine;
    }

    public LocalDate getExpectedDate() {
        return expectedDate;
    }

    public BigDecimal getIssuedQuantity() {
        return issuedQuantity;
    }

    public BigDecimal getDeliveredQuantity() {
        return deliveredQuantity;
    }

    public BigDecimal getInvoicedQuantity() {
        return invoicedQuantity;
    }

    public BigDecimal getReturnedQuantity() {
        return returnedQuantity;
    }

    public BigDecimal getClosedQuantity() {
        return closedQuantity;
    }
}
