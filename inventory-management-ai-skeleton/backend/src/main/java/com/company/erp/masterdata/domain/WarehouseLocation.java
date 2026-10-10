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

/** Vị trí trong kho (BR-MD-40). Mỗi kho có đúng một vị trí mặc định, tạo cùng kho; vị trí mặc định không ngừng được. */
@Entity
@Table(name = "warehouse_locations")
public class WarehouseLocation extends AuditableEntity {

    public static final String DEFAULT_CODE = "MAC_DINH";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "warehouse_id", nullable = false, updatable = false)
    private Long warehouseId;

    @Column(name = "code", nullable = false, length = 30, updatable = false)
    private String code;

    @Column(name = "name", nullable = false, length = 200)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "zone_type", nullable = false, length = 20)
    private LocationZone zoneType;

    @Column(name = "storage_condition", length = 200)
    private String storageCondition;

    @Column(name = "is_default", nullable = false, updatable = false)
    private boolean defaultLocation;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private RecordStatus status;

    protected WarehouseLocation() {
    }

    public WarehouseLocation(Long warehouseId, String code, String name, LocationZone zoneType,
                             String storageCondition, boolean defaultLocation) {
        this.warehouseId = warehouseId;
        this.code = code;
        this.defaultLocation = defaultLocation;
        this.status = RecordStatus.ACTIVE;
        update(name, zoneType, storageCondition);
    }

    public static WarehouseLocation defaultFor(Long warehouseId) {
        return new WarehouseLocation(warehouseId, DEFAULT_CODE, "Vị trí mặc định", LocationZone.NORMAL, null, true);
    }

    public final void update(String name, LocationZone zoneType, String storageCondition) {
        this.name = name;
        this.zoneType = zoneType;
        this.storageCondition = storageCondition;
    }

    public void changeStatus(RecordStatus status) {
        this.status = status;
    }

    public Long getId() {
        return id;
    }

    public Long getWarehouseId() {
        return warehouseId;
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public LocationZone getZoneType() {
        return zoneType;
    }

    public String getStorageCondition() {
        return storageCondition;
    }

    public boolean isDefaultLocation() {
        return defaultLocation;
    }

    public RecordStatus getStatus() {
        return status;
    }
}
