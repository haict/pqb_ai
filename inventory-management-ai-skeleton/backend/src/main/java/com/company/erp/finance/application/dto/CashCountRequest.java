package com.company.erp.finance.application.dto;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;

/** Lập / sửa phiếu kiểm kê quỹ tiền mặt; {@code documentDate} là ngày kiểm kê. Sửa gửi {@code version}. */
public record CashCountRequest(
        @NotNull Long branchId,
        @NotNull Long cashAccountId,
        @NotNull LocalDate documentDate,
        LocalDate postingDate,
        @NotNull @PositiveOrZero @Digits(integer = 15, fraction = 4) BigDecimal countedAmount,
        @Size(max = 500) String description,
        Integer version) {
}
