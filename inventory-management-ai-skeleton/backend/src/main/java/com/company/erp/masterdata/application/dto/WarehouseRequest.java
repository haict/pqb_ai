package com.company.erp.masterdata.application.dto;

import com.company.erp.masterdata.domain.VirtualWarehouseKind;
import com.company.erp.masterdata.domain.WarehouseType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record WarehouseRequest(
        @NotNull Long branchId,
        @NotBlank @Pattern(regexp = "[A-Z0-9_.-]{1,30}", message = "Mã gồm 1-30 ký tự A-Z, 0-9, _ . -") String code,
        @NotBlank @Size(max = 200) String name,
        @NotNull WarehouseType warehouseType,
        VirtualWarehouseKind virtualKind,
        @Size(max = 500) String address,
        @Size(max = 200) String storageCondition) {
}
