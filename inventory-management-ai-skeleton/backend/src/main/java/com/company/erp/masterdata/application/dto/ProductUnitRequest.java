package com.company.erp.masterdata.application.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;

/** Đơn vị giao dịch mới: 1 đơn vị = {@code conversionFactor} đơn vị cơ sở, hiệu lực từ ngày (BR-MD-20, BR-MD-21). */
public record ProductUnitRequest(
        @NotNull Long unitId,
        @NotNull @DecimalMin(value = "0", inclusive = false) @Digits(integer = 13, fraction = 6) BigDecimal conversionFactor,
        @NotNull LocalDate effectiveFrom) {
}
