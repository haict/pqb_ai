package com.company.erp.inventory.domain;

import com.company.erp.common.domain.AuditableEntity;
import com.company.erp.common.domain.BusinessException;
import com.company.erp.common.domain.ErrorCode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;

/** Giữ chỗ tồn cho chứng từ nguồn (BR mục 5.4): giảm tồn khả dụng, không đổi tồn thực tế. */
@Entity
@Table(name = "inventory_reservations")
public class InventoryReservation extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "company_id", nullable = false, updatable = false)
    private Long companyId;

    @Column(name = "product_id", nullable = false, updatable = false)
    private Long productId;

    @Column(name = "warehouse_id", nullable = false, updatable = false)
    private Long warehouseId;

    @Column(name = "lot_id", updatable = false)
    private Long lotId;

    @Column(name = "serial_id", updatable = false)
    private Long serialId;

    @Column(name = "source_type", nullable = false, length = 30, updatable = false)
    private String sourceType;

    @Column(name = "source_id", nullable = false, updatable = false)
    private Long sourceId;

    @Column(name = "source_line_id", updatable = false)
    private Long sourceLineId;

    @Column(name = "quantity", nullable = false, precision = 19, scale = 3, updatable = false)
    private BigDecimal quantity;

    @Column(name = "consumed_quantity", nullable = false, precision = 19, scale = 3)
    private BigDecimal consumedQuantity;

    @Column(name = "released_quantity", nullable = false, precision = 19, scale = 3)
    private BigDecimal releasedQuantity;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private ReservationStatus status;

    @Column(name = "expires_at")
    private Instant expiresAt;

    protected InventoryReservation() {
    }

    public InventoryReservation(Long companyId, Long productId, Long warehouseId, String sourceType, Long sourceId,
                                Long sourceLineId, BigDecimal quantity, Instant expiresAt) {
        this.companyId = companyId;
        this.productId = productId;
        this.warehouseId = warehouseId;
        this.sourceType = sourceType;
        this.sourceId = sourceId;
        this.sourceLineId = sourceLineId;
        this.quantity = quantity;
        this.consumedQuantity = BigDecimal.ZERO;
        this.releasedQuantity = BigDecimal.ZERO;
        this.status = ReservationStatus.ACTIVE;
        this.expiresAt = expiresAt;
    }

    /** Số còn giữ chỗ. */
    public BigDecimal remaining() {
        return quantity.subtract(consumedQuantity).subtract(releasedQuantity);
    }

    /** Nhả phần còn lại; trả số đã nhả để giảm tổng giữ chỗ. */
    public BigDecimal release() {
        if (status != ReservationStatus.ACTIVE) {
            throw new BusinessException(ErrorCode.INVALID_DOCUMENT_STATE, "Giữ chỗ đã kết thúc.");
        }
        BigDecimal remaining = remaining();
        this.releasedQuantity = releasedQuantity.add(remaining);
        this.status = ReservationStatus.RELEASED;
        return remaining;
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

    public String getSourceType() {
        return sourceType;
    }

    public Long getSourceId() {
        return sourceId;
    }

    public BigDecimal getQuantity() {
        return quantity;
    }

    public ReservationStatus getStatus() {
        return status;
    }
}
