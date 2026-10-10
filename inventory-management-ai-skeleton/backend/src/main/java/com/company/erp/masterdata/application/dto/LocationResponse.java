package com.company.erp.masterdata.application.dto;

import com.company.erp.common.domain.RecordStatus;
import com.company.erp.masterdata.domain.LocationZone;

public record LocationResponse(
        Long id,
        Long warehouseId,
        String code,
        String name,
        LocationZone zoneType,
        String storageCondition,
        boolean defaultLocation,
        RecordStatus status,
        int version) {
}
