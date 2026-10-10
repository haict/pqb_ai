package com.company.erp.masterdata.application.dto;

import com.company.erp.masterdata.domain.LocationZone;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record UpdateLocationRequest(
        @NotBlank @Size(max = 200) String name,
        @NotNull LocationZone zoneType,
        @Size(max = 200) String storageCondition,
        @NotNull Integer version) {
}
