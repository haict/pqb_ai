package com.company.erp.inventory.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import org.hibernate.annotations.Immutable;

/**
 * Tổng tồn theo (hàng, kho): tồn thực tế, giữ chỗ, đang về (BR mục 5.4). Tồn khả dụng = tồn ĐẠT chưa hết hạn, không
 * khóa − giữ chỗ (tính khi cần, không lưu). Dòng này là khóa đầu tiên khi ghi sổ; chỉ cập nhật bằng câu lệnh riêng.
 */
@Entity
@Immutable
@Table(name = "inventory_stock_summaries")
public class StockSummary {

    @Id
    private Long id;

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(name = "warehouse_id", nullable = false)
    private Long warehouseId;

    @Column(name = "on_hand_quantity", nullable = false, precision = 19, scale = 3)
    private BigDecimal onHandQuantity;

    @Column(name = "reserved_quantity", nullable = false, precision = 19, scale = 3)
    private BigDecimal reservedQuantity;

    @Column(name = "incoming_quantity", nullable = false, precision = 19, scale = 3)
    private BigDecimal incomingQuantity;

    @Column(name = "version", nullable = false)
    private int version;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected StockSummary() {
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

    public BigDecimal getOnHandQuantity() {
        return onHandQuantity;
    }

    public BigDecimal getReservedQuantity() {
        return reservedQuantity;
    }

    public BigDecimal getIncomingQuantity() {
        return incomingQuantity;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
