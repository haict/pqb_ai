package com.company.erp.sales.domain;

import com.company.erp.common.domain.QualityStatus;
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

/** Hàng thực xuất của dòng giao (lô, hạn dùng, serial, trạng thái chất lượng), ghi khi xuất kho (BR-SAL-22). */
@Entity
@Table(name = "sales_delivery_items")
public class SalesDeliveryItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "lot_id")
    private Long lotId;

    @Column(name = "lot_no")
    private String lotNo;

    @Column(name = "expiry_date")
    private LocalDate expiryDate;

    @Column(name = "serial_no")
    private String serialNo;

    @Enumerated(EnumType.STRING)
    @Column(name = "quality_status", nullable = false)
    private QualityStatus qualityStatus;

    @Column(name = "base_quantity", nullable = false, precision = 19, scale = 3)
    private BigDecimal baseQuantity;

    protected SalesDeliveryItem() {
    }

    public SalesDeliveryItem(Long lotId, String lotNo, LocalDate expiryDate, String serialNo,
                             QualityStatus qualityStatus, BigDecimal baseQuantity) {
        this.lotId = lotId;
        this.lotNo = lotNo;
        this.expiryDate = expiryDate;
        this.serialNo = serialNo;
        this.qualityStatus = qualityStatus;
        this.baseQuantity = baseQuantity;
    }

    public Long getId() {
        return id;
    }

    public Long getLotId() {
        return lotId;
    }

    public String getLotNo() {
        return lotNo;
    }

    public LocalDate getExpiryDate() {
        return expiryDate;
    }

    public String getSerialNo() {
        return serialNo;
    }

    public QualityStatus getQualityStatus() {
        return qualityStatus;
    }

    public BigDecimal getBaseQuantity() {
        return baseQuantity;
    }
}
