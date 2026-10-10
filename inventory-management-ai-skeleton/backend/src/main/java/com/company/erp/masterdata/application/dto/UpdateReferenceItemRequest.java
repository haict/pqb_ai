package com.company.erp.masterdata.application.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public record UpdateReferenceItemRequest(
        @NotBlank @Size(max = 200) String name,
        BigDecimal numericValue,
        @NotNull Integer version) {
}
