package com.company.erp.system.application.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record UpdateBranchRequest(
        @NotBlank @Size(max = 255) String name,
        @Size(max = 500) String address,
        @Size(max = 30) String phone,
        @NotNull Integer version) {
}
