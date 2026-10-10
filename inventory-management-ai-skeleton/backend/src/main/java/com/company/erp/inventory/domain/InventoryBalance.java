package com.company.erp.inventory.domain;

import com.company.erp.common.domain.QualityStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import org.hibernate.annotations.Immutable;

/**
 * Tồn thực tế chi tiết (bản đọc). Chỉ {@code InventoryPostingService} cập nhật, bằng câu lệnh có điều kiện không âm;
 * entity chỉ để đọc nên đánh dấu {@code @Immutable}.
 */
@Entity
@Immutable
@Table(name = "inventory_balances")
public class InventoryBalance {

    @Id
    private Long id;

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(name = "warehouse_id", nullable = false)
    private Long warehouseId;

    @Column(name = "location_id", nullable = false)
    private Long locationId;

    @Column(name = "lot_id")
    private Long lotId;

    @Column(name = "serial_id")
    private Long serialId;

    @Enumerated(EnumType.STRING)
    @Column(name = "quality_status", nullable = false, length = 30)
    private QualityStatus qualityStatus;

    @Column(name = "on_hand_quantity", nullable = false, precision = 19, scale = 3)
    private BigDecimal onHandQuantity;

    @Column(name = "version", nullable = false)
    private int version;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected InventoryBalance() {
    }

    public StockKey key() {
        return new StockKey(productId, warehouseId, locationId, lotId, serialId, qualityStatus);
    }

    public Long getId() {
        return id;
    }

    public Long getCompanyId() {
        return companyId;
    }

    public Long getProductId() {
        return productId;
    }

    public Long getWarehouseId() {
        return warehouseId;
    }

    public Long getLocationId() {
        return locationId;
    }

    public Long getLotId() {
        return lotId;
    }

    public Long getSerialId() {
        return serialId;
    }

    public QualityStatus getQualityStatus() {
        return qualityStatus;
    }

    public BigDecimal getOnHandQuantity() {
        return onHandQuantity;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
