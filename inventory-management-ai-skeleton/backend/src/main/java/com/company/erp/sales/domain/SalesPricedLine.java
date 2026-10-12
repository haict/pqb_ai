package com.company.erp.sales.domain;

import jakarta.persistence.Column;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Cột giá chung của dòng báo giá / đơn bán: giá đề xuất (theo bảng giá, BR-SAL-02), giá sàn, đơn giá, chiết khấu dòng
 * (BR-SAL-35), thuế. Thành tiền = số lượng × đơn giá × (1 − chiết khấu %), làm tròn đến đồng; thuế tính trên thành tiền
 * (D-05). Dòng hàng tặng (khuyến mại) giá trị 0 và không xét giá sàn `[Chưa kiểm chứng]` (D-26).
 */
@MappedSuperclass
public abstract class SalesPricedLine {

    protected static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

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

    @Column(name = "list_price", precision = 19, scale = 4)
    private BigDecimal listPrice;

    @Column(name = "floor_price", precision = 19, scale = 4)
    private BigDecimal floorPrice;

    @Column(name = "price_source")
    private String priceSource;

    @Column(name = "unit_price", nullable = false, precision = 19, scale = 4)
    private BigDecimal unitPrice;

    @Column(name = "discount_percent", nullable = false, precision = 7, scale = 4)
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

    @Column(name = "promotional", nullable = false)
    private boolean promotional;

    @Column(name = "description")
    private String description;

    /** Giá trị dòng đã kiểm tra ở tầng ứng dụng. */
    public record PricedValues(Long productId, Long unitId, BigDecimal conversionFactor, BigDecimal quantity,
                               BigDecimal baseQuantity, BigDecimal listPrice, BigDecimal floorPrice,
                               String priceSource, BigDecimal unitPrice, BigDecimal discountPercent, Long taxRateId,
                               BigDecimal taxRate, boolean promotional, String description) {
    }

    protected SalesPricedLine() {
    }

    protected SalesPricedLine(int lineNo) {
        this.lineNo = lineNo;
    }

    protected void applyPrice(PricedValues values) {
        this.productId = values.productId();
        this.unitId = values.unitId();
        this.conversionFactor = values.conversionFactor();
        this.quantity = values.quantity();
        this.baseQuantity = values.baseQuantity();
        this.listPrice = values.listPrice();
        this.floorPrice = values.floorPrice();
        this.priceSource = values.priceSource();
        this.promotional = values.promotional();
        this.unitPrice = promotional ? BigDecimal.ZERO : values.unitPrice();
        this.discountPercent = promotional || values.discountPercent() == null ? BigDecimal.ZERO
                : values.discountPercent();
        this.taxRateId = promotional ? null : values.taxRateId();
        this.taxRate = promotional ? null : values.taxRate();
        this.description = values.description();
        BigDecimal gross = quantity.multiply(unitPrice).setScale(0, RoundingMode.HALF_UP);
        this.amount = quantity.multiply(netUnitPrice()).setScale(0, RoundingMode.HALF_UP);
        this.discountAmount = gross.subtract(amount).max(BigDecimal.ZERO);
        this.taxAmount = taxRate == null ? BigDecimal.ZERO
                : amount.multiply(taxRate).divide(HUNDRED).setScale(0, RoundingMode.HALF_UP);
    }

    protected PricedValues priceValues() {
        return new PricedValues(productId, unitId, conversionFactor, quantity, baseQuantity, listPrice, floorPrice,
                priceSource, unitPrice, discountPercent, taxRateId, taxRate, promotional, description);
    }

    /** Giá, chiết khấu, thuế của dòng cho số lượng khác (dòng hóa đơn / trả hàng theo dòng đơn). */
    public PricedValues valuesFor(BigDecimal newQuantity, BigDecimal newBaseQuantity, String newDescription) {
        return new PricedValues(productId, unitId, conversionFactor, newQuantity, newBaseQuantity, listPrice,
                floorPrice, priceSource, unitPrice, discountPercent, taxRateId, taxRate, promotional, newDescription);
    }

    /** Đơn giá sau chiết khấu (theo đơn vị giao dịch). */
    public BigDecimal netUnitPrice() {
        return unitPrice.multiply(HUNDRED.subtract(discountPercent)).divide(HUNDRED, 4, RoundingMode.HALF_UP);
    }

    /** Giá sau chiết khấu dưới giá sàn (BR-SAL-03). */
    public boolean belowFloor() {
        return !promotional && floorPrice != null && netUnitPrice().compareTo(floorPrice) < 0;
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

    public BigDecimal getListPrice() {
        return listPrice;
    }

    public BigDecimal getFloorPrice() {
        return floorPrice;
    }

    public String getPriceSource() {
        return priceSource;
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

    public boolean isPromotional() {
        return promotional;
    }

    public String getDescription() {
        return description;
    }
}
