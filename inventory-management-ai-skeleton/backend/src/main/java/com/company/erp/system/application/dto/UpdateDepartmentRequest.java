package com.company.erp.system.application.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record UpdateDepartmentRequest(
        @NotBlank @Size(max = 255) String name,
        @NotNull Integer version) {
}
