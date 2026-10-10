package com.company.erp.system.application.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record UpdateParameterRequest(
        @NotNull Long companyId,
        @NotBlank @Size(max = 500) String value,
        @NotNull @Min(0) Integer version) {
}
