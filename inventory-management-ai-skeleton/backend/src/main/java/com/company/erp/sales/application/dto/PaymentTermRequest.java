package com.company.erp.sales.application.dto;

import com.company.erp.sales.domain.PaymentDueBasis;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

/** Đợt thu tiền (BR-SAL-50): tỷ lệ % tổng đơn, mốc tính hạn, số ngày sau mốc. */
public record PaymentTermRequest(
        @NotNull @Positive @DecimalMax("100") @Digits(integer = 3, fraction = 4) BigDecimal percent,
        @NotNull PaymentDueBasis dueBasis,
        @NotNull @PositiveOrZero @Max(3650) Integer dueDays,
        @Size(max = 500) String description) {
}
