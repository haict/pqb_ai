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
import java.time.LocalDate;

/** Dòng đề nghị mua; {@code orderedQuantity}, {@code closedQuantity} theo đơn vị cơ sở. */
@Entity
@Table(name = "purchase_requisition_lines")
public class PurchaseRequisitionLine {

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

    @Column(name = "ordered_quantity", nullable = false, precision = 19, scale = 3)
    private BigDecimal orderedQuantity = BigDecimal.ZERO;

    @Column(name = "closed_quantity", nullable = false, precision = 19, scale = 3)
    private BigDecimal closedQuantity = BigDecimal.ZERO;

    @Column(name = "suggested_supplier_id")
    private Long suggestedSupplierId;

    @Column(name = "required_date")
    private LocalDate requiredDate;

    @Column(name = "note")
    private String note;

    protected PurchaseRequisitionLine() {
    }

    @SuppressWarnings("java:S107")
    public PurchaseRequisitionLine(int lineNo, Long productId, Long unitId, BigDecimal conversionFactor,
                                   BigDecimal quantity, BigDecimal baseQuantity, Long suggestedSupplierId,
                                   LocalDate requiredDate, String note) {
        this.lineNo = lineNo;
        this.productId = productId;
        this.unitId = unitId;
        this.conversionFactor = conversionFactor;
        this.quantity = quantity;
        this.baseQuantity = baseQuantity;
        this.suggestedSupplierId = suggestedSupplierId;
        this.requiredDate = requiredDate;
        this.note = note;
    }

    void copyFrom(PurchaseRequisitionLine other) {
        this.productId = other.productId;
        this.unitId = other.unitId;
        this.conversionFactor = other.conversionFactor;
        this.quantity = other.quantity;
        this.baseQuantity = other.baseQuantity;
        this.suggestedSupplierId = other.suggestedSupplierId;
        this.requiredDate = other.requiredDate;
        this.note = other.note;
    }

    /** Số còn chưa đặt mua (đơn vị cơ sở). */
    public BigDecimal remainingQuantity() {
        return baseQuantity.subtract(orderedQuantity).subtract(closedQuantity).max(BigDecimal.ZERO);
    }

    /**
     * Ghi nhận số đặt mua khi đơn mua được duyệt ({@code delta} dương) hoặc hủy ({@code delta} âm). Không đặt vượt số
     * còn lại (BR mục 5.5).
     */
    void addOrdered(BigDecimal delta, String field) {
        if (delta.signum() > 0 && delta.compareTo(remainingQuantity()) > 0) {
            throw new BusinessException(ErrorCode.SOURCE_QUANTITY_EXCEEDED, field + ": đề nghị mua dòng " + lineNo
                    + " còn " + remainingQuantity().stripTrailingZeros().toPlainString() + ", không đặt "
                    + delta.stripTrailingZeros().toPlainString() + " được.");
        }
        orderedQuantity = orderedQuantity.add(delta).max(BigDecimal.ZERO);
    }

    void closeRemaining() {
        closedQuantity = closedQuantity.add(remainingQuantity());
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

    public BigDecimal getOrderedQuantity() {
        return orderedQuantity;
    }

    public BigDecimal getClosedQuantity() {
        return closedQuantity;
    }

    public Long getSuggestedSupplierId() {
        return suggestedSupplierId;
    }

    public LocalDate getRequiredDate() {
        return requiredDate;
    }

    public String getNote() {
        return note;
    }
}
