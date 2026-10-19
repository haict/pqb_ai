package com.company.erp.finance.application.dto;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;

/** Dòng công nợ đầu kỳ (D-60); để trống hạn thanh toán thì tính = ngày chứng từ + số ngày nợ của đối tượng. */
public record DebtOpeningLineRequest(
        @NotNull Long partnerId,
        LocalDate dueDate,
        @NotNull @Positive @Digits(integer = 15, fraction = 4) BigDecimal amount,
        Long salesUserId,
        @Size(max = 500) String note) {
}
