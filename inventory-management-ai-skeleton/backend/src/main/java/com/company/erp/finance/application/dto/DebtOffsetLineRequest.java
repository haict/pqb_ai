package com.company.erp.finance.application.dto;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

/** Dòng bù trừ: khoản công nợ và số tiền trừ. */
public record DebtOffsetLineRequest(
        @NotNull Long debtItemId,
        @NotNull @Positive @Digits(integer = 15, fraction = 4) BigDecimal amount,
        @Size(max = 500) String description) {
}
