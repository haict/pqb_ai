package com.company.erp.system.application.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record UpdateUserRequest(
        @NotBlank @Size(max = 150) String fullName,
        @Email @Size(max = 150) String email,
        @Size(max = 30) String phone,
        Long defaultBranchId,
        boolean ownDataOnly,
        @NotNull Integer version) {
}
