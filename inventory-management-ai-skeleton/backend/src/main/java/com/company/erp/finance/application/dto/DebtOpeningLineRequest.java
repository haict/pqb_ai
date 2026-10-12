package com.company.erp.finance.application.dto;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;

/** Dòng công nợ đầu kỳ; để trống hạn thanh toán thì tính = ngày hóa đơn + số ngày nợ của đối tượng (BR-AR-02). */
public record DebtOpeningLineRequest(
        @NotNull Long partnerId,
        @NotBlank @Size(max = 50) String invoiceNo,
        @NotNull LocalDate invoiceDate,
        LocalDate dueDate,
        @NotNull @Positive @Digits(integer = 15, fraction = 4) BigDecimal amount,
        Long salesUserId,
        @Size(max = 500) String note) {
}
