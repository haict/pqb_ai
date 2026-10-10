package com.company.erp.masterdata.domain;

import com.company.erp.common.domain.BusinessException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Objects;

/** Định mức tồn tối thiểu / tối đa của hàng tại một kho (BR-MD-09, BR-INV-40). */
@Entity
@IdClass(ProductStockLevel.Key.class)
@Table(name = "product_stock_levels")
public class ProductStockLevel {

    @Id
    @Column(name = "product_id")
    private Long productId;

    @Id
    @Column(name = "warehouse_id")
    private Long warehouseId;

    @Column(name = "min_quantity", precision = 19, scale = 3)
    private BigDecimal minQuantity;

    @Column(name = "max_quantity", precision = 19, scale = 3)
    private BigDecimal maxQuantity;

    protected ProductStockLevel() {
    }

    public ProductStockLevel(Long productId, Long warehouseId, BigDecimal minQuantity, BigDecimal maxQuantity) {
        this.productId = productId;
        this.warehouseId = warehouseId;
        change(minQuantity, maxQuantity);
    }

    public final void change(BigDecimal minQuantity, BigDecimal maxQuantity) {
        if (minQuantity != null && maxQuantity != null && minQuantity.compareTo(maxQuantity) > 0) {
            throw BusinessException.invalidField("maxQuantity", "Định mức tối đa phải lớn hơn hoặc bằng tối thiểu.");
        }
        this.minQuantity = minQuantity;
        this.maxQuantity = maxQuantity;
    }

    public Long getProductId() {
        return productId;
    }

    public Long getWarehouseId() {
        return warehouseId;
    }

    public BigDecimal getMinQuantity() {
        return minQuantity;
    }

    public BigDecimal getMaxQuantity() {
        return maxQuantity;
    }

    /** Khóa chính (hàng, kho). */
    public static class Key implements Serializable {

        private Long productId;
        private Long warehouseId;

        public Key() {
        }

        public Key(Long productId, Long warehouseId) {
            this.productId = productId;
            this.warehouseId = warehouseId;
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof Key key && Objects.equals(productId, key.productId)
                    && Objects.equals(warehouseId, key.warehouseId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(productId, warehouseId);
        }
    }
}
