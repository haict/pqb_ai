package com.company.erp.finance.application.dto;

import com.company.erp.finance.domain.AdjustmentDirection;
import java.math.BigDecimal;
import java.time.LocalDate;

/** Dòng điều chỉnh kèm hóa đơn bị giảm và tài khoản đối ứng. */
public record DebtAdjustmentLineResponse(
        Long id,
        int lineNo,
        AdjustmentDirection direction,
        Long debtItemId,
        String debtItemLabel,
        Long accountId,
        String accountCode,
        String accountName,
        Long costItemId,
        BigDecimal amount,
        String invoiceNo,
        LocalDate dueDate,
        String description) {
}
