package com.company.erp.inventory.domain;

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
import java.time.Instant;

/** Dòng kiểm kê: số tồn chụp lúc bắt đầu và số đếm gốc (BR-INV-31). Chênh lệch = đếm − chụp. */
@Entity
@Table(name = "stock_count_lines")
public class StockCountLine {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "product_id", nullable = false, updatable = false)
    private Long productId;

    @Column(name = "location_id", nullable = false, updatable = false)
    private Long locationId;

    @Column(name = "lot_id", updatable = false)
    private Long lotId;

    @Column(name = "serial_id", updatable = false)
    private Long serialId;

    @Enumerated(EnumType.STRING)
    @Column(name = "quality_status", nullable = false, length = 30, updatable = false)
    private QualityStatus qualityStatus;

    @Column(name = "snapshot_quantity", nullable = false, precision = 19, scale = 3, updatable = false)
    private BigDecimal snapshotQuantity;

    @Column(name = "counted_quantity", precision = 19, scale = 3)
    private BigDecimal countedQuantity;

    @Column(name = "counted_by")
    private Long countedBy;

    @Column(name = "counted_at")
    private Instant countedAt;

    @Column(name = "note", length = 500)
    private String note;

    protected StockCountLine() {
    }

    public StockCountLine(StockKey key, BigDecimal snapshotQuantity) {
        this.productId = key.productId();
        this.locationId = key.locationId();
        this.lotId = key.lotId();
        this.serialId = key.serialId();
        this.qualityStatus = key.qualityStatus();
        this.snapshotQuantity = snapshotQuantity;
    }

    public void count(BigDecimal quantity, Long actorId, Instant now, String note) {
        this.countedQuantity = quantity;
        this.countedBy = actorId;
        this.countedAt = now;
        this.note = note;
    }

    /** Chênh lệch; dòng chưa đếm coi như không chênh lệch thì sai, nên bắt buộc đếm đủ trước khi gửi duyệt. */
    public BigDecimal difference() {
        return countedQuantity == null ? BigDecimal.ZERO : countedQuantity.subtract(snapshotQuantity);
    }

    public StockKey key(Long warehouseId) {
        return new StockKey(productId, warehouseId, locationId, lotId, serialId, qualityStatus);
    }

    public Long getId() {
        return id;
    }

    public Long getProductId() {
        return productId;
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

    public BigDecimal getSnapshotQuantity() {
        return snapshotQuantity;
    }

    public BigDecimal getCountedQuantity() {
        return countedQuantity;
    }

    public Long getCountedBy() {
        return countedBy;
    }

    public Instant getCountedAt() {
        return countedAt;
    }

    public String getNote() {
        return note;
    }
}
