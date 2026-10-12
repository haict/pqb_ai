package com.company.erp.finance.application.dto;

import com.company.erp.finance.domain.AdjustmentDirection;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Dòng điều chỉnh: giảm ({@code debtItemId} bắt buộc) hoặc tăng (số hóa đơn, hạn tùy chọn); tài khoản đối ứng bắt buộc.
 */
public record DebtAdjustmentLineRequest(
        @NotNull AdjustmentDirection direction,
        Long debtItemId,
        @NotNull Long accountId,
        Long costItemId,
        @NotNull @Positive @Digits(integer = 15, fraction = 4) BigDecimal amount,
        @Size(max = 50) String invoiceNo,
        LocalDate dueDate,
        @Size(max = 500) String description) {
}
