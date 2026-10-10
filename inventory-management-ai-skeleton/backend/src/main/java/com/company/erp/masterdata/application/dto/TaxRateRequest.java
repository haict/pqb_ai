package com.company.erp.masterdata.application.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;

public record TaxRateRequest(
        @NotNull Long companyId,
        @NotBlank @Pattern(regexp = "[A-Z0-9_.-]{1,20}", message = "Mã gồm 1-20 ký tự A-Z, 0-9, _ . -") String code,
        @NotBlank @Size(max = 100) String name,
        @NotNull @DecimalMin("0") @DecimalMax("100") BigDecimal rate,
        @NotNull LocalDate validFrom,
        LocalDate validTo) {
}
