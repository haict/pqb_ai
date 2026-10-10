package com.company.erp.inventory.application.dto;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;

/** Chính sách xuất vượt tồn khả dụng cho một kho (và một hàng, nếu chọn) trong thời hạn (BR-GEN-31, D-10). */
public record StockIssuePolicyRequest(
        @NotNull Long warehouseId,
        Long productId,
        @NotNull @Positive @Digits(integer = 16, fraction = 3) BigDecimal maxQuantity,
        @NotNull LocalDate validFrom,
        @NotNull LocalDate validTo,
        @NotBlank @Size(max = 1000) String reason) {
}
