package com.company.erp.masterdata.application.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PartnerContactDto(
        @NotBlank @Size(max = 200) String name,
        @Size(max = 100) String position,
        @Size(max = 30) String phone,
        @Email @Size(max = 255) String email,
        boolean primaryContact) {
}
