package com.company.erp.finance.application.dto;

import com.company.erp.finance.domain.DebtType;
import java.math.BigDecimal;
import java.time.LocalDate;

/** Dòng bù trừ kèm thông tin khoản công nợ. */
public record DebtOffsetLineResponse(
        Long id,
        int lineNo,
        Long debtItemId,
        DebtType debtType,
        String invoiceNo,
        LocalDate dueDate,
        BigDecimal remainingAmount,
        BigDecimal amount,
        String description) {
}
