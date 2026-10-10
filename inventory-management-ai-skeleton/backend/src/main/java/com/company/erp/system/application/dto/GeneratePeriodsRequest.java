package com.company.erp.system.application.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/** Tạo 12 kỳ tháng của một năm; tháng đã có thì bỏ qua. */
public record GeneratePeriodsRequest(
        @NotNull Long companyId,
        @NotNull @Min(2000) @Max(2100) Integer year) {
}
