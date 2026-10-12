package com.company.erp.finance.application.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Dòng sổ quỹ: tiền vào, tiền ra, số dư lũy kế sau dòng. */
public record CashBookLineResponse(
        Long id,
        LocalDate postingDate,
        String referenceType,
        Long referenceId,
        String referenceNo,
        String transactionCode,
        Long partnerId,
        String partnerName,
        String counterpartyName,
        String description,
        boolean reversal,
        BigDecimal inAmount,
        BigDecimal outAmount,
        BigDecimal balance) {
}
