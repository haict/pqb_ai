package com.company.erp.sales.domain;

import com.company.erp.common.domain.BusinessException;
import com.company.erp.common.domain.ErrorCode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.math.BigDecimal;

/**
 * Dòng hóa đơn bán: hàng hóa theo dòng phiếu giao đã xuất kho, dịch vụ theo dòng đơn bán; giá, chiết khấu, thuế theo
 * dòng đơn bán. Theo dõi số đã trả lại và giá trị đã giảm (trả hàng, giảm giá hàng bán, trước thuế).
 */
@Entity
@Table(name = "sales_invoice_lines")
public class SalesInvoiceLine extends SalesPricedLine {

    @Column(name = "delivery_line_id")
    private Long deliveryLineId;

    @Column(name = "order_line_id", nullable = false)
    private Long orderLineId;

    @Column(name = "returned_quantity", nullable = false, precision = 19, scale = 3)
    private BigDecimal returnedQuantity = BigDecimal.ZERO;

    @Column(name = "adjusted_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal adjustedAmount = BigDecimal.ZERO;

    protected SalesInvoiceLine() {
    }

    public SalesInvoiceLine(int lineNo, Long deliveryLineId, Long orderLineId, PricedValues values) {
        super(lineNo);
        this.deliveryLineId = deliveryLineId;
        this.orderLineId = orderLineId;
        applyPrice(values);
    }

    void copyFrom(SalesInvoiceLine other) {
        this.deliveryLineId = other.deliveryLineId;
        this.orderLineId = other.orderLineId;
        applyPrice(other.priceValues());
    }

    public boolean isGoods() {
        return deliveryLineId != null;
    }

    /** Số còn trả lại được (đơn vị cơ sở). */
    public BigDecimal returnableQuantity() {
        return getBaseQuantity().subtract(returnedQuantity).max(BigDecimal.ZERO);
    }

    /** Giá trị trước thuế còn giảm được. */
    public BigDecimal remainingAmount() {
        return getAmount().subtract(adjustedAmount).max(BigDecimal.ZERO);
    }

    /** Trả hàng bán (dương) / đảo (âm): số lượng và giá trị giảm (BR-SAL-32). */
    void addReturn(BigDecimal baseDelta, BigDecimal amountDelta, String field) {
        if (baseDelta.signum() > 0 && baseDelta.compareTo(returnableQuantity()) > 0) {
            throw new BusinessException(ErrorCode.SOURCE_QUANTITY_EXCEEDED, field + ": dòng " + getLineNo()
                    + " của hóa đơn chỉ còn trả được " + plain(returnableQuantity()) + " (đơn vị cơ sở).");
        }
        returnedQuantity = returnedQuantity.add(baseDelta).max(BigDecimal.ZERO);
        addAdjusted(amountDelta, field);
    }

    /** Giảm giá hàng bán (dương) / đảo (âm), không vượt giá trị còn lại (BR-SAL-36). */
    void addAdjusted(BigDecimal delta, String field) {
        if (delta.signum() > 0 && delta.compareTo(remainingAmount()) > 0) {
            throw new BusinessException(ErrorCode.SOURCE_QUANTITY_EXCEEDED, field + ": dòng " + getLineNo()
                    + " của hóa đơn chỉ còn giá trị " + plain(remainingAmount()) + ".");
        }
        adjustedAmount = adjustedAmount.add(delta).max(BigDecimal.ZERO);
    }

    boolean hasDependents() {
        return returnedQuantity.signum() > 0 || adjustedAmount.signum() > 0;
    }

    private static String plain(BigDecimal value) {
        return value.stripTrailingZeros().toPlainString();
    }

    public Long getDeliveryLineId() {
        return deliveryLineId;
    }

    public Long getOrderLineId() {
        return orderLineId;
    }

    public BigDecimal getReturnedQuantity() {
        return returnedQuantity;
    }

    public BigDecimal getAdjustedAmount() {
        return adjustedAmount;
    }
}
