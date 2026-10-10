package com.company.erp.masterdata.application.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;

public record UpdateTaxRateRequest(
        @NotBlank @Size(max = 100) String name,
        @NotNull @DecimalMin("0") @DecimalMax("100") BigDecimal rate,
        @NotNull LocalDate validFrom,
        LocalDate validTo,
        @NotNull Integer version) {
}
