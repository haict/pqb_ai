package com.company.erp.sales.application.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Dòng đơn bán. {@code unitPrice} trống: lấy giá theo thứ tự ưu tiên (BR-SAL-02); {@code discountPercent} trống:
 * chiết khấu đại lý của khách (nếu có); {@code taxRateId} trống: thuế suất mặc định của hàng. {@code promotional}: hàng
 * tặng kèm, giá trị 0. {@code lotNo} trống: kho chọn theo FEFO khi ghi sổ; chỉ định lô khác lô FEFO phải ghi
 * {@code lotOverrideReason} (BR-INV-21). {@code serialNumbers}: serial chỉ định (hàng quản lý serial; trống: kho chọn).
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
        @Size(max = 50) String lotNo,
        @Size(max = 500) String lotOverrideReason,
        @Size(max = 1000) List<@NotBlank @Size(max = 100) String> serialNumbers,
        @Size(max = 500) String description) {
}
