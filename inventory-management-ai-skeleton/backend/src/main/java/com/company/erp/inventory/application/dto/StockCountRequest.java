package com.company.erp.inventory.application.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

public record StockCountRequest(
        @NotNull Long warehouseId,
        @NotNull LocalDate countDate,
        @Size(max = 1000) String note) {
}
