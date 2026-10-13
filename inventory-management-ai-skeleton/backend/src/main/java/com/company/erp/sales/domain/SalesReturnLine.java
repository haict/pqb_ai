package com.company.erp.sales.domain;

import com.company.erp.common.domain.QualityStatus;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
 * Dòng trả hàng / giảm giá hàng bán theo dòng đơn bán đã ghi sổ. Trả hàng: đúng lô, hạn dùng, serial đã xuất; thành
 * tiền = số lượng × đơn giá sau chiết khấu của dòng đơn. Giảm giá: chỉ có thành tiền. Thuế theo dòng đơn; tiền làm
 * tròn đến đồng (D-05).
 */
@Entity
@Table(name = "sales_return_lines")
public class SalesReturnLine {

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "line_no", nullable = false)
    private int lineNo;

    @Column(name = "order_line_id", nullable = false)
    private Long orderLineId;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(name = "unit_id", nullable = false)
    private Long unitId;

    @Column(name = "conversion_factor", nullable = false, precision = 19, scale = 6)
    private BigDecimal conversionFactor;

    @Column(name = "quantity", precision = 19, scale = 3)
    private BigDecimal quantity;

    @Column(name = "base_quantity", precision = 19, scale = 3)
    private BigDecimal baseQuantity;

    @Column(name = "lot_no")
    private String lotNo;

    @Column(name = "expiry_date")
    private LocalDate expiryDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "quality_status")
    private QualityStatus qualityStatus;

    @Column(name = "unit_price", precision = 19, scale = 4)
    private BigDecimal unitPrice;

    @Column(name = "amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    @Column(name = "tax_rate_id")
    private Long taxRateId;

    @Column(name = "tax_rate", precision = 7, scale = 4)
    private BigDecimal taxRate;

    @Column(name = "tax_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal taxAmount;

    @Column(name = "description")
    private String description;

    @ElementCollection
    @CollectionTable(name = "sales_return_serials", joinColumns = @JoinColumn(name = "line_id"))
    @Column(name = "serial_no", length = 100)
    @OrderBy
    private List<String> serialNumbers = new ArrayList<>();

    protected SalesReturnLine() {
    }

    /**
     * Giá trị dòng đã kiểm tra ở tầng ứng dụng. Trả hàng: {@code quantity}, {@code unitPrice}; giảm giá:
     * {@code amount} (trước thuế).
     */
    public record Values(Long orderLineId, Long productId, Long unitId,
                         BigDecimal conversionFactor, BigDecimal quantity, BigDecimal baseQuantity, String lotNo,
                         LocalDate expiryDate, QualityStatus qualityStatus, BigDecimal unitPrice, BigDecimal amount, Long taxRateId,
                         BigDecimal taxRate, String description, List<String> serialNumbers) {
    }

    public SalesReturnLine(int lineNo, Values values) {
        this.lineNo = lineNo;
        apply(values);
    }

    private void apply(Values values) {
        this.orderLineId = values.orderLineId();
        this.productId = values.productId();
        this.unitId = values.unitId();
        this.conversionFactor = values.conversionFactor();
        this.quantity = values.quantity();
        this.baseQuantity = values.baseQuantity();
        this.lotNo = values.lotNo();
        this.expiryDate = values.expiryDate();
        this.qualityStatus = values.qualityStatus();
        this.unitPrice = values.unitPrice();
        this.taxRateId = values.taxRateId();
        this.taxRate = values.taxRate();
        this.description = values.description();
        this.amount = quantity != null ? quantity.multiply(unitPrice).setScale(0, RoundingMode.HALF_UP)
                : values.amount().setScale(0, RoundingMode.HALF_UP);
        this.taxAmount = taxRate == null ? BigDecimal.ZERO
                : amount.multiply(taxRate).divide(HUNDRED).setScale(0, RoundingMode.HALF_UP);
        this.serialNumbers.clear();
        this.serialNumbers.addAll(values.serialNumbers());
    }

    void copyFrom(SalesReturnLine other) {
        apply(new Values(other.orderLineId, other.productId, other.unitId,
                other.conversionFactor, other.quantity, other.baseQuantity, other.lotNo, other.expiryDate,
                other.qualityStatus,
                other.unitPrice, other.amount, other.taxRateId, other.taxRate, other.description,
                new ArrayList<>(other.serialNumbers)));
    }

    public Long getId() {
        return id;
    }

    public int getLineNo() {
        return lineNo;
    }

    public Long getOrderLineId() {
        return orderLineId;
    }

    public LocalDate getExpiryDate() {
        return expiryDate;
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

    public String getLotNo() {
        return lotNo;
    }

    public QualityStatus getQualityStatus() {
        return qualityStatus;
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

    public String getDescription() {
        return description;
    }

    public List<String> getSerialNumbers() {
        return Collections.unmodifiableList(serialNumbers);
    }
}
