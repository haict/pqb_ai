package com.company.erp.system.application.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateRoleRequest(
        @NotBlank @Pattern(regexp = "[A-Z0-9_]{2,50}", message = "Mã gồm 2-50 ký tự A-Z, 0-9, _") String code,
        @NotBlank @Size(max = 150) String name,
        @Size(max = 500) String description) {
}
