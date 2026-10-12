package com.company.erp.purchasing.application.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;

/**
 * Lập / sửa đơn mua. {@code paymentTermDays} bỏ trống thì lấy số ngày nợ nhà cung cấp; {@code currencyCode} bỏ trống
 * là VND (P1 chỉ VND, D-24). Sửa gửi {@code version}; chi nhánh không đổi sau khi lập.
 */
public record PurchaseOrderRequest(
        @NotNull Long branchId,
        @NotNull LocalDate documentDate,
        @NotNull Long supplierId,
        @NotNull Long warehouseId,
        LocalDate expectedDate,
        @PositiveOrZero @Max(3650) Integer paymentTermDays,
        @Pattern(regexp = "[A-Z]{3}") String currencyCode,
        @Size(max = 500) String description,
        @NotEmpty @Valid @Size(max = 200) List<PurchaseOrderLineRequest> lines,
        Integer version) {
}
