package com.company.erp.finance.application.dto;

import com.company.erp.finance.domain.DebtEntryType;
import java.math.BigDecimal;
import java.time.LocalDate;

/** Dòng sổ chi tiết công nợ kèm số dư lũy kế (theo chiều tự nhiên: phải thu dư Nợ, phải trả dư Có). */
public record DebtLedgerLineResponse(
        Long id,
        LocalDate postingDate,
        DebtEntryType entryType,
        String referenceType,
        Long referenceId,
        String referenceNo,
        Long debtItemId,
        String description,
        BigDecimal debitAmount,
        BigDecimal creditAmount,
        BigDecimal balance) {
}
