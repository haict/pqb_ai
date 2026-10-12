package com.company.erp.sales.application.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Dòng báo giá / đơn bán. {@code unitPrice} trống: lấy giá theo thứ tự ưu tiên (BR-SAL-02); {@code discountPercent}
 * trống: chiết khấu đại lý của khách (nếu có); {@code taxRateId} trống: thuế suất mặc định của hàng.
 * {@code promotional}: hàng tặng kèm, giá trị 0. {@code expectedDate} chỉ dùng cho đơn bán.
 */
public record SalesLineRequest(
        @NotNull Long productId,
        @NotNull Long unitId,
        @NotNull @Positive @Digits(integer = 16, fraction = 3) BigDecimal quantity,
        @PositiveOrZero @Digits(integer = 15, fraction = 4) BigDecimal unitPrice,
        @PositiveOrZero @DecimalMax("100") @Digits(integer = 3, fraction = 4) BigDecimal discountPercent,
        Long taxRateId,
        Boolean promotional,
        LocalDate expectedDate,
        @Size(max = 500) String description) {
}
