package com.company.erp.purchasing.domain;

import com.company.erp.common.domain.BusinessException;
import com.company.erp.common.domain.ErrorCode;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Dòng đơn mua (BR-PUR-04, D-54): {@code amount} = số lượng × đơn giá, chiết khấu theo %, thuế tính trên tiền sau chiết
 * khấu; tiền làm tròn đến đồng (D-05). Dòng hàng hóa mang lô / hạn dùng / serial để nhập kho khi ghi sổ; dòng dịch vụ
 * không qua kho (BR-PUR-16). Sau ghi sổ theo dõi số đã trả, giá trị đã giảm, chi phí đã phân bổ (BR-PUR-12, 14).
 */
@Entity
@Table(name = "purchase_order_lines")
public class PurchaseOrderLine {

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "line_no", nullable = false)
    private int lineNo;

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

    @Column(name = "discount_percent", nullable = false, precision = 9, scale = 4)
    private BigDecimal discountPercent;

    @Column(name = "discount_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal discountAmount;

    @Column(name = "amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    @Column(name = "tax_rate_id")
    private Long taxRateId;

    @Column(name = "tax_rate", precision = 7, scale = 4)
    private BigDecimal taxRate;

    @Column(name = "tax_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal taxAmount;

    @Column(name = "service_line", nullable = false)
    private boolean serviceLine;

    @Column(name = "location_id")
    private Long locationId;

    @Column(name = "lot_no")
    private String lotNo;

    @Column(name = "manufacture_date")
    private LocalDate manufactureDate;

    @Column(name = "expiry_date")
    private LocalDate expiryDate;

    @Column(name = "returned_quantity", nullable = false, precision = 19, scale = 3)
    private BigDecimal returnedQuantity = BigDecimal.ZERO;

    @Column(name = "adjusted_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal adjustedAmount = BigDecimal.ZERO;

    @Column(name = "allocated_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal allocatedAmount = BigDecimal.ZERO;

    @Column(name = "note")
    private String note;

    @ElementCollection
    @CollectionTable(name = "purchase_order_serials", joinColumns = @JoinColumn(name = "line_id"))
    @Column(name = "serial_no", length = 100)
    @OrderBy
    private List<String> serialNumbers = new ArrayList<>();

    protected PurchaseOrderLine() {
    }

    /** Giá trị dòng; tiền tính lại từ số lượng, đơn giá, chiết khấu, thuế suất (không nhận tiền từ client). */
    public record Values(Long productId, Long unitId, BigDecimal conversionFactor, BigDecimal quantity,
                         BigDecimal baseQuantity, BigDecimal unitPrice, BigDecimal discountPercent, Long taxRateId,
                         BigDecimal taxRate, boolean serviceLine, Long locationId, String lotNo,
                         LocalDate manufactureDate, LocalDate expiryDate, List<String> serialNumbers, String note) {
    }

    public PurchaseOrderLine(int lineNo, Values values) {
        this.lineNo = lineNo;
        apply(values);
    }

    private void apply(Values values) {
        this.productId = values.productId();
        this.unitId = values.unitId();
        this.conversionFactor = values.conversionFactor();
        this.quantity = values.quantity();
        this.baseQuantity = values.baseQuantity();
        this.unitPrice = values.unitPrice();
        this.discountPercent = values.discountPercent() == null ? BigDecimal.ZERO : values.discountPercent();
        this.taxRateId = values.taxRateId();
        this.taxRate = values.taxRate();
        this.serviceLine = values.serviceLine();
        this.locationId = values.locationId();
        this.lotNo = values.lotNo();
        this.manufactureDate = values.manufactureDate();
        this.expiryDate = values.expiryDate();
        this.note = values.note();
        this.serialNumbers.clear();
        this.serialNumbers.addAll(values.serialNumbers());
        this.amount = money(quantity.multiply(unitPrice));
        this.discountAmount = money(amount.multiply(discountPercent).divide(HUNDRED));
        this.taxAmount = taxRate == null ? BigDecimal.ZERO
                : money(amount.subtract(discountAmount).multiply(taxRate).divide(HUNDRED));
    }

    void copyFrom(PurchaseOrderLine other) {
        apply(new Values(other.productId, other.unitId, other.conversionFactor, other.quantity, other.baseQuantity,
                other.unitPrice, other.discountPercent, other.taxRateId, other.taxRate, other.serviceLine,
                other.locationId, other.lotNo, other.manufactureDate, other.expiryDate,
                new ArrayList<>(other.serialNumbers), other.note));
    }

    static BigDecimal money(BigDecimal value) {
        return value.setScale(0, RoundingMode.HALF_UP);
    }

    /** Thành tiền sau chiết khấu, trước thuế (giá trị nhập kho, BR-PUR-10). */
    public BigDecimal netAmount() {
        return amount.subtract(discountAmount);
    }

    /** Đơn giá nhập kho cho một đơn vị của dòng (sau chiết khấu, trước thuế). */
    public BigDecimal netUnitPrice() {
        return netAmount().divide(quantity, 4, RoundingMode.HALF_UP);
    }

    /** Số còn trả lại được (đơn vị cơ sở). */
    public BigDecimal returnableQuantity() {
        return serviceLine ? BigDecimal.ZERO : baseQuantity.subtract(returnedQuantity).max(BigDecimal.ZERO);
    }

    /** Giá trị trước thuế còn giảm / phân bổ được. */
    public BigDecimal remainingAmount() {
        return netAmount().subtract(adjustedAmount).subtract(allocatedAmount).max(BigDecimal.ZERO);
    }

    /** Trả hàng (BR-PUR-14) ghi sổ (dương) / đảo (âm): số lượng và giá trị giảm. */
    void addReturn(BigDecimal baseDelta, BigDecimal amountDelta, String field) {
        if (baseDelta.signum() > 0 && baseDelta.compareTo(returnableQuantity()) > 0) {
            throw new BusinessException(ErrorCode.SOURCE_QUANTITY_EXCEEDED, field + ": dòng " + lineNo
                    + " của đơn mua chỉ còn trả được " + returnableQuantity().stripTrailingZeros().toPlainString()
                    + " (đơn vị cơ sở).");
        }
        returnedQuantity = returnedQuantity.add(baseDelta).max(BigDecimal.ZERO);
        addAdjusted(amountDelta, field);
    }

    /** Giảm giá hàng mua (dương) / đảo (âm); không vượt giá trị còn lại của dòng. */
    void addAdjusted(BigDecimal delta, String field) {
        if (delta.signum() > 0 && delta.compareTo(remainingAmount()) > 0) {
            throw new BusinessException(ErrorCode.SOURCE_QUANTITY_EXCEEDED, field + ": dòng " + lineNo
                    + " của đơn mua chỉ còn giá trị " + remainingAmount().stripTrailingZeros().toPlainString() + ".");
        }
        adjustedAmount = adjustedAmount.add(delta).max(BigDecimal.ZERO);
    }

    /** Phân bổ chi phí mua (BR-PUR-12) ghi sổ (dương) / đảo (âm); chỉ dòng dịch vụ. */
    void addAllocated(BigDecimal delta, String field) {
        if (delta.signum() > 0 && (!serviceLine || delta.compareTo(remainingAmount()) > 0)) {
            throw new BusinessException(ErrorCode.SOURCE_QUANTITY_EXCEEDED, field + ": dòng " + lineNo
                    + " của đơn mua " + (serviceLine ? "chỉ còn " + remainingAmount().stripTrailingZeros()
                    .toPlainString() + " chưa phân bổ." : "là hàng hóa, không phân bổ được."));
        }
        allocatedAmount = allocatedAmount.add(delta).max(BigDecimal.ZERO);
    }

    /** Đã có trả hàng / giảm giá / phân bổ ghi sổ theo dòng (đảo đơn mua phải đảo các chứng từ này trước). */
    boolean hasDependents() {
        return returnedQuantity.signum() > 0 || adjustedAmount.signum() > 0 || allocatedAmount.signum() > 0;
    }

    public Long getId() {
        return id;
    }

    public int getLineNo() {
        return lineNo;
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

    public BigDecimal getDiscountPercent() {
        return discountPercent;
    }

    public BigDecimal getDiscountAmount() {
        return discountAmount;
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

    public boolean isServiceLine() {
        return serviceLine;
    }

    public Long getLocationId() {
        return locationId;
    }

    public String getLotNo() {
        return lotNo;
    }

    public LocalDate getManufactureDate() {
        return manufactureDate;
    }

    public LocalDate getExpiryDate() {
        return expiryDate;
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

    public String getNote() {
        return note;
    }

    public List<String> getSerialNumbers() {
        return Collections.unmodifiableList(serialNumbers);
    }
}
