package com.company.erp.masterdata.application.dto;

import com.company.erp.masterdata.domain.ReferenceCategory;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public record ReferenceItemRequest(
        @NotNull Long companyId,
        @NotNull ReferenceCategory category,
        @NotBlank @Pattern(regexp = "[A-Z0-9_.-]{1,30}", message = "Mã gồm 1-30 ký tự A-Z, 0-9, _ . -") String code,
        @NotBlank @Size(max = 200) String name,
        BigDecimal numericValue) {
}
