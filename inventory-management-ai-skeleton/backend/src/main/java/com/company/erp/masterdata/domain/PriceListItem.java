package com.company.erp.masterdata.domain;

import com.company.erp.common.domain.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;

/** Giá của một hàng theo đơn vị tính và bậc số lượng tối thiểu trong bảng giá (BR-MD-42). */
@Entity
@Table(name = "price_list_items")
public class PriceListItem extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "price_list_id", nullable = false, updatable = false)
    private Long priceListId;

    @Column(name = "product_id", nullable = false, updatable = false)
    private Long productId;

    @Column(name = "unit_id", nullable = false, updatable = false)
    private Long unitId;

    @Column(name = "min_quantity", nullable = false, precision = 19, scale = 3, updatable = false)
    private BigDecimal minQuantity;

    @Column(name = "unit_price", nullable = false, precision = 19, scale = 4)
    private BigDecimal unitPrice;

    protected PriceListItem() {
    }

    public PriceListItem(Long priceListId, Long productId, Long unitId, BigDecimal minQuantity, BigDecimal unitPrice) {
        this.priceListId = priceListId;
        this.productId = productId;
        this.unitId = unitId;
        this.minQuantity = minQuantity;
        this.unitPrice = unitPrice;
    }

    public void changePrice(BigDecimal unitPrice) {
        this.unitPrice = unitPrice;
    }

    public Long getId() {
        return id;
    }

    public Long getPriceListId() {
        return priceListId;
    }

    public Long getProductId() {
        return productId;
    }

    public Long getUnitId() {
        return unitId;
    }

    public BigDecimal getMinQuantity() {
        return minQuantity;
    }

    public BigDecimal getUnitPrice() {
        return unitPrice;
    }
}
