package com.company.erp.system.application.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateDepartmentRequest(
        @NotNull Long branchId,
        @NotBlank @Pattern(regexp = "[A-Z0-9_]{1,20}", message = "Mã gồm 1-20 ký tự A-Z, 0-9, _") String code,
        @NotBlank @Size(max = 255) String name) {
}
