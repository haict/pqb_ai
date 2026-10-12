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

    /**
     * Xuất kho theo chứng từ nguồn dùng phần giữ chỗ: trừ tối đa phần còn lại; hết thì CONSUMED. Trả số đã dùng để giảm
     * tổng giữ chỗ.
     */
    public BigDecimal consume(BigDecimal requested) {
        if (status != ReservationStatus.ACTIVE) {
            return BigDecimal.ZERO;
        }
        BigDecimal used = requested.min(remaining()).max(BigDecimal.ZERO);
        this.consumedQuantity = consumedQuantity.add(used);
        if (remaining().signum() == 0) {
            this.status = ReservationStatus.CONSUMED;
        }
        return used;
    }

    /**
     * Hoàn phần đã dùng khi đảo phiếu xuất: giữ chỗ còn hiệu lực / đã dùng hết thì giữ lại phần đó (trả số cần cộng lại
     * vào tổng giữ chỗ); giữ chỗ đã nhả thì phần hoàn coi như nhả luôn (trả 0).
     */
    public BigDecimal restore(BigDecimal quantityToRestore) {
        BigDecimal back = quantityToRestore.min(consumedQuantity).max(BigDecimal.ZERO);
        this.consumedQuantity = consumedQuantity.subtract(back);
        if (status == ReservationStatus.RELEASED) {
            this.releasedQuantity = releasedQuantity.add(back);
            return BigDecimal.ZERO;
        }
        if (back.signum() > 0) {
            this.status = ReservationStatus.ACTIVE;
        }
        return back;
    }

    public BigDecimal getConsumedQuantity() {
        return consumedQuantity;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public Long getSourceLineId() {
        return sourceLineId;
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
