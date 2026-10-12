package com.company.erp.purchasing.domain;

import com.company.erp.common.domain.BusinessException;
import com.company.erp.common.domain.ErrorCode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Dòng hóa đơn mua: hàng hóa theo dòng phiếu nhận hàng đã nhập kho, hoặc dịch vụ không qua kho (có thể theo dòng đơn
 * mua). Thành tiền = số lượng × đơn giá, thuế tính trên thành tiền; tiền làm tròn đến đồng (D-05). Dòng hàng khuyến
 * mại (BR-PUR-17) có giá trị 0 `[Giả định]`.
 */
@Entity
@Table(name = "purchase_invoice_lines")
public class PurchaseInvoiceLine {

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "line_no", nullable = false)
    private int lineNo;

    @Column(name = "receipt_line_id")
    private Long receiptLineId;

    @Column(name = "order_line_id")
    private Long orderLineId;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(name = "unit_id", nullable = false)
    private Long unitId;

    @Column(name = "conversion_factor", nullable = false, precision = 19, scale = 6)
    private BigDecimal conversionFactor;

    @Column(name = "quantity", nullable = false, precision = 19, scale = 3)
    private BigDecimal quantity;

    @Column(name = "base_quantity", nullable = false, precision = 19, scale = 3)
    private BigDecimal baseQuantity;

    @Column(name = "unit_price", nullable = false, precision = 19, scale = 4)
    private BigDecimal unitPrice;

    @Column(name = "amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    @Column(name = "tax_rate_id")
    private Long taxRateId;

    @Column(name = "tax_rate", precision = 7, scale = 4)
    private BigDecimal taxRate;

    @Column(name = "tax_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal taxAmount;

    @Column(name = "promotional", nullable = false)
    private boolean promotional;

    @Column(name = "returned_quantity", nullable = false, precision = 19, scale = 3)
    private BigDecimal returnedQuantity = BigDecimal.ZERO;

    @Column(name = "adjusted_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal adjustedAmount = BigDecimal.ZERO;

    @Column(name = "allocated_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal allocatedAmount = BigDecimal.ZERO;

    @Column(name = "description")
    private String description;

    protected PurchaseInvoiceLine() {
    }

    public record Values(Long receiptLineId, Long orderLineId, Long productId, Long unitId,
                         BigDecimal conversionFactor, BigDecimal quantity, BigDecimal baseQuantity,
                         BigDecimal unitPrice, Long taxRateId, BigDecimal taxRate, boolean promotional,
                         String description) {
    }

    public PurchaseInvoiceLine(int lineNo, Values values) {
        this.lineNo = lineNo;
        apply(values);
    }

    private void apply(Values values) {
        this.receiptLineId = values.receiptLineId();
        this.orderLineId = values.orderLineId();
        this.productId = values.productId();
        this.unitId = values.unitId();
        this.conversionFactor = values.conversionFactor();
        this.quantity = values.quantity();
        this.baseQuantity = values.baseQuantity();
        this.promotional = values.promotional();
        this.unitPrice = promotional ? BigDecimal.ZERO : values.unitPrice();
        this.taxRateId = promotional ? null : values.taxRateId();
        this.taxRate = promotional ? null : values.taxRate();
        this.description = values.description();
        this.amount = quantity.multiply(unitPrice).setScale(0, RoundingMode.HALF_UP);
        this.taxAmount = taxRate == null ? BigDecimal.ZERO
                : amount.multiply(taxRate).divide(HUNDRED).setScale(0, RoundingMode.HALF_UP);
    }

    void copyFrom(PurchaseInvoiceLine other) {
        apply(new Values(other.receiptLineId, other.orderLineId, other.productId, other.unitId,
                other.conversionFactor, other.quantity, other.baseQuantity, other.unitPrice, other.taxRateId,
                other.taxRate, other.promotional, other.description));
    }

    /** Số còn trả lại được (đơn vị cơ sở). */
    public BigDecimal returnableQuantity() {
        return baseQuantity.subtract(returnedQuantity).max(BigDecimal.ZERO);
    }

    /** Giá trị trước thuế còn giảm / phân bổ được. */
    public BigDecimal remainingAmount() {
        return amount.subtract(adjustedAmount).subtract(allocatedAmount).max(BigDecimal.ZERO);
    }

    /** Trả hàng (BR-PUR-14) ghi sổ (dương) / đảo (âm): số lượng và giá trị giảm. */
    void addReturn(BigDecimal baseDelta, BigDecimal amountDelta, String field) {
        if (baseDelta.signum() > 0 && baseDelta.compareTo(returnableQuantity()) > 0) {
            throw new BusinessException(ErrorCode.SOURCE_QUANTITY_EXCEEDED, field + ": dòng " + lineNo
                    + " của hóa đơn chỉ còn trả được " + returnableQuantity().stripTrailingZeros().toPlainString()
                    + " (đơn vị cơ sở).");
        }
        returnedQuantity = returnedQuantity.add(baseDelta).max(BigDecimal.ZERO);
        addAdjusted(amountDelta, field);
    }

    /** Giảm giá hàng mua (dương) / đảo (âm); không vượt giá trị còn lại của dòng. */
    void addAdjusted(BigDecimal delta, String field) {
        if (delta.signum() > 0 && delta.compareTo(remainingAmount()) > 0) {
            throw new BusinessException(ErrorCode.SOURCE_QUANTITY_EXCEEDED, field + ": dòng " + lineNo
                    + " của hóa đơn chỉ còn giá trị " + remainingAmount().stripTrailingZeros().toPlainString() + ".");
        }
        adjustedAmount = adjustedAmount.add(delta).max(BigDecimal.ZERO);
    }

    /** Phân bổ chi phí mua (BR-PUR-12) ghi sổ (dương) / đảo (âm); chỉ dòng dịch vụ. */
    void addAllocated(BigDecimal delta, String field) {
        if (delta.signum() > 0 && (isGoods() || delta.compareTo(remainingAmount()) > 0)) {
            throw new BusinessException(ErrorCode.SOURCE_QUANTITY_EXCEEDED, field + ": dòng " + lineNo
                    + " của hóa đơn " + (isGoods() ? "là hàng hóa, không phân bổ được."
                    : "chỉ còn " + remainingAmount().stripTrailingZeros().toPlainString() + " chưa phân bổ."));
        }
        allocatedAmount = allocatedAmount.add(delta).max(BigDecimal.ZERO);
    }

    /** Đã có trả hàng / giảm giá / phân bổ ghi sổ theo dòng (đảo hóa đơn phải đảo các chứng từ này trước). */
    public boolean hasDependents() {
        return returnedQuantity.signum() > 0 || adjustedAmount.signum() > 0 || allocatedAmount.signum() > 0;
    }

    public BigDecimal getReturnedQuantity() {
        return returnedQuantity;
    }

    public BigDecimal getAdjustedAmount() {
        return adjustedAmount;
    }

    public BigDecimal getAllocatedAmount() {
        return allocatedAmount;
    }

    public boolean isGoods() {
        return receiptLineId != null;
    }

    public Long getId() {
        return id;
    }

    public int getLineNo() {
        return lineNo;
    }

    public Long getReceiptLineId() {
        return receiptLineId;
    }

    public Long getOrderLineId() {
        return orderLineId;
    }

    public Long getProductId() {
        return productId;
    }

    public Long getUnitId() {
        return unitId;
    }

    public BigDecimal getConversionFactor() {
        return conversionFactor;
    }

    public BigDecimal getQuantity() {
        return quantity;
    }

    public BigDecimal getBaseQuantity() {
        return baseQuantity;
    }

    public BigDecimal getUnitPrice() {
        return unitPrice;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public Long getTaxRateId() {
        return taxRateId;
    }

    public BigDecimal getTaxRate() {
        return taxRate;
    }

    public BigDecimal getTaxAmount() {
        return taxAmount;
    }

    public boolean isPromotional() {
        return promotional;
    }

    public String getDescription() {
        return description;
    }
}
