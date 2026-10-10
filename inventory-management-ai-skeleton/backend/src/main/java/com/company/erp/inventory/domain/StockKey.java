package com.company.erp.inventory.domain;

import com.company.erp.common.domain.QualityStatus;
import java.util.Comparator;
import java.util.Objects;

/**
 * Khóa tồn chi tiết: (hàng, kho, vị trí, lô, serial, trạng thái chất lượng) - mức theo dõi tồn của BR mục 9.1.
 * Thứ tự so sánh dùng để khóa dòng số dư theo thứ tự xác định, tránh deadlock (business-integrity.md mục 5).
 */
public record StockKey(Long productId, Long warehouseId, Long locationId, Long lotId, Long serialId,
                       QualityStatus qualityStatus) implements Comparable<StockKey> {

    private static final Comparator<StockKey> ORDER = Comparator.comparing(StockKey::productId)
            .thenComparing(StockKey::warehouseId)
            .thenComparing(StockKey::locationId)
            .thenComparing(key -> key.lotId() == null ? 0L : key.lotId())
            .thenComparing(key -> key.serialId() == null ? 0L : key.serialId())
            .thenComparing(StockKey::qualityStatus);

    public StockKey {
        Objects.requireNonNull(productId, "productId");
        Objects.requireNonNull(warehouseId, "warehouseId");
        Objects.requireNonNull(locationId, "locationId");
        Objects.requireNonNull(qualityStatus, "qualityStatus");
    }

    public StockKey withStatus(QualityStatus status) {
        return new StockKey(productId, warehouseId, locationId, lotId, serialId, status);
    }

    public StockKey withPlace(Long warehouse, Long location) {
        return new StockKey(productId, warehouse, location, lotId, serialId, qualityStatus);
    }

    @Override
    public int compareTo(StockKey other) {
        return ORDER.compare(this, other);
    }
}
