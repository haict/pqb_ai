package com.company.erp.masterdata.domain;

import com.company.erp.common.domain.BusinessException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Kho thuộc một chi nhánh (BR-MD-40, BR-GEN-60); kho ảo có loại (BR-MD-41). Chi nhánh và loại kho không đổi. */
@Entity
@Table(name = "warehouses")
public class Warehouse extends CodeNameEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "branch_id", nullable = false, updatable = false)
    private Long branchId;

    @Enumerated(EnumType.STRING)
    @Column(name = "warehouse_type", nullable = false, length = 20, updatable = false)
    private WarehouseType warehouseType;

    @Enumerated(EnumType.STRING)
    @Column(name = "virtual_kind", length = 20, updatable = false)
    private VirtualWarehouseKind virtualKind;

    @Column(name = "address", length = 500)
    private String address;

    @Column(name = "storage_condition", length = 200)
    private String storageCondition;

    protected Warehouse() {
    }

    public Warehouse(Long companyId, Long branchId, String code, String name, WarehouseType warehouseType,
                     VirtualWarehouseKind virtualKind, String address, String storageCondition) {
        super(companyId, code, name);
        if ((warehouseType == WarehouseType.VIRTUAL) != (virtualKind != null)) {
            throw BusinessException.invalidField("virtualKind", "Kho ảo phải chọn loại kho ảo; kho thường thì không.");
        }
        this.branchId = branchId;
        this.warehouseType = warehouseType;
        this.virtualKind = virtualKind;
        update(name, address, storageCondition);
    }

    public final void update(String name, String address, String storageCondition) {
        rename(name);
        this.address = address;
        this.storageCondition = storageCondition;
    }

    @Override
    public Long getId() {
        return id;
    }

    public Long getBranchId() {
        return branchId;
    }

    public WarehouseType getWarehouseType() {
        return warehouseType;
    }

    public VirtualWarehouseKind getVirtualKind() {
        return virtualKind;
    }

    public String getAddress() {
        return address;
    }

    public String getStorageCondition() {
        return storageCondition;
    }
}
