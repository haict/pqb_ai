package com.company.erp.masterdata.application.dto;

import com.company.erp.common.domain.RecordStatus;
import com.company.erp.masterdata.domain.VirtualWarehouseKind;
import com.company.erp.masterdata.domain.WarehouseType;
import java.time.Instant;

public record WarehouseResponse(
        Long id,
        Long companyId,
        Long branchId,
        String code,
        String name,
        WarehouseType warehouseType,
        VirtualWarehouseKind virtualKind,
        String address,
        String storageCondition,
        RecordStatus status,
        int version,
        Instant updatedAt) {
}
