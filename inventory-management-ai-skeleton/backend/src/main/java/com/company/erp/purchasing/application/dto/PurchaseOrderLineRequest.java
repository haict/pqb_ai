package com.company.erp.purchasing.application.dto;

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
 * Dòng đơn mua. Tiền (thành tiền, chiết khấu, thuế) do server tính. {@code taxRateId} bỏ trống thì lấy thuế suất mặc
 * định của hàng. Hàng hóa: lô / hạn dùng theo cấu hình hàng, serial cho hàng quản lý serial (số serial = số lượng theo
 * đơn vị cơ sở), vị trí trong kho; dịch vụ bỏ trống các trường này. Hàng nhiều lô: mỗi lô một dòng.
 */
public record PurchaseOrderLineRequest(
        @NotNull Long productId,
        @NotNull Long unitId,
        @NotNull @Positive @Digits(integer = 16, fraction = 3) BigDecimal quantity,
        @NotNull @PositiveOrZero @Digits(integer = 15, fraction = 4) BigDecimal unitPrice,
        @PositiveOrZero @DecimalMax("100") @Digits(integer = 3, fraction = 4) BigDecimal discountPercent,
        Long taxRateId,
        Long locationId,
        @Size(max = 50) String lotNo,
        LocalDate manufactureDate,
        LocalDate expiryDate,
        @Size(max = 1000) List<@NotBlank @Size(max = 100) String> serialNumbers,
        @Size(max = 500) String note) {
}
