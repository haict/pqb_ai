package com.company.erp.purchasing.application.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Dòng đơn mua. Tiền (thành tiền, chiết khấu, thuế) do server tính. {@code taxRateId} bỏ trống thì lấy thuế suất mặc
 * định của hàng; {@code requisitionLineId}: dòng đề nghị mua được đặt.
 */
public record PurchaseOrderLineRequest(
        @NotNull Long productId,
        @NotNull Long unitId,
        @NotNull @Positive @Digits(integer = 16, fraction = 3) BigDecimal quantity,
        @NotNull @PositiveOrZero @Digits(integer = 15, fraction = 4) BigDecimal unitPrice,
        @PositiveOrZero @DecimalMax("100") @Digits(integer = 3, fraction = 4) BigDecimal discountPercent,
        Long taxRateId,
        LocalDate expectedDate,
        Long requisitionLineId,
        @Size(max = 500) String note) {
}
