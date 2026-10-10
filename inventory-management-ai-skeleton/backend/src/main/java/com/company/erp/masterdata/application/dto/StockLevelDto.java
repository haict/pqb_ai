package com.company.erp.masterdata.application.dto;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.math.BigDecimal;

/** Định mức tồn của hàng tại một kho (BR-MD-09). */
public record StockLevelDto(
        @NotNull Long warehouseId,
        @PositiveOrZero @Digits(integer = 16, fraction = 3) BigDecimal minQuantity,
        @PositiveOrZero @Digits(integer = 16, fraction = 3) BigDecimal maxQuantity) {
}
