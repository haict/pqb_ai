package com.company.erp.system.application.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record UpdateCompanyRequest(
        @NotBlank @Size(max = 255) String name,
        @Size(max = 20) String taxCode,
        @Size(max = 500) String address,
        @Size(max = 30) String phone,
        @Email @Size(max = 150) String email,
        @NotNull Integer version) {
}
