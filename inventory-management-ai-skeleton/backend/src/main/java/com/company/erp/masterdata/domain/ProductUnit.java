package com.company.erp.masterdata.domain;

import com.company.erp.common.domain.AuditableEntity;
import com.company.erp.common.domain.RecordStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Đơn vị giao dịch của hàng: {@code 1 đơn vị này = conversionFactor đơn vị cơ sở}, hiệu lực từ {@code effectiveFrom}
 * (BR-MD-20). Hệ số đã tạo không sửa; đổi hệ số thì tạo dòng mới có hiệu lực từ ngày khác (BR-MD-21).
 */
@Entity
@Table(name = "product_units")
public class ProductUnit extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "product_id", nullable = false, updatable = false)
    private Long productId;

    @Column(name = "unit_id", nullable = false, updatable = false)
    private Long unitId;

    @Column(name = "conversion_factor", nullable = false, precision = 19, scale = 6, updatable = false)
    private BigDecimal conversionFactor;

    @Column(name = "effective_from", nullable = false, updatable = false)
    private LocalDate effectiveFrom;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private RecordStatus status;

    protected ProductUnit() {
    }

    public ProductUnit(Long productId, Long unitId, BigDecimal conversionFactor, LocalDate effectiveFrom) {
        this.productId = productId;
        this.unitId = unitId;
        this.conversionFactor = conversionFactor;
        this.effectiveFrom = effectiveFrom;
        this.status = RecordStatus.ACTIVE;
    }

    public void changeStatus(RecordStatus status) {
        this.status = status;
    }

    public Long getId() {
        return id;
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

    public LocalDate getEffectiveFrom() {
        return effectiveFrom;
    }

    public RecordStatus getStatus() {
        return status;
    }
}
