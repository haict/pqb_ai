package com.company.erp.purchasing.application.dto;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;

/** Dòng chi phí cần phân bổ: dòng dịch vụ của đơn mua đã ghi sổ và số tiền (trước thuế, không vượt phần còn lại). */
public record CostAllocationSourceRequest(
        @NotNull Long orderLineId,
        @NotNull @Positive @Digits(integer = 15, fraction = 0) BigDecimal amount) {
}
