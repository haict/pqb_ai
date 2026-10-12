package com.company.erp.finance.application.dto;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

/**
 * Dòng chứng từ thu chi: {@code debtItemId} (phân bổ chỉ định vào hóa đơn) hoặc {@code accountId} (tài khoản đối ứng
 * của thu chi khác), không cả hai.
 */
public record CashDocumentLineRequest(
        Long debtItemId,
        Long accountId,
        Long costItemId,
        @NotNull @Positive @Digits(integer = 15, fraction = 4) BigDecimal amount,
        @Size(max = 500) String description) {
}
