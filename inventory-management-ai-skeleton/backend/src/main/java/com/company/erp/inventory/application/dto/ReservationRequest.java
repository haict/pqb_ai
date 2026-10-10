package com.company.erp.inventory.application.dto;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;

/** Giữ chỗ tồn cho chứng từ nguồn (phân hệ bán hàng dùng; Giai đoạn 3 cung cấp dịch vụ và API kiểm thử). */
public record ReservationRequest(
        @NotNull Long companyId,
        @NotNull Long productId,
        @NotNull Long warehouseId,
        @NotBlank String sourceType,
        @NotNull Long sourceId,
        Long sourceLineId,
        @NotNull @Positive @Digits(integer = 16, fraction = 3) BigDecimal quantity) {
}
