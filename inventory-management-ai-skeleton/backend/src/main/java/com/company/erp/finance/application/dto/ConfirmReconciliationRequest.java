package com.company.erp.finance.application.dto;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

/** Số dư đối tượng xác nhận trên biên bản đối chiếu (có thể âm khi đối tượng trả trước). */
public record ConfirmReconciliationRequest(
        @NotNull @Digits(integer = 15, fraction = 4) BigDecimal confirmedBalance,
        @Size(max = 255) String contactName,
        @Size(max = 1000) String note) {
}
