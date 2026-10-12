package com.company.erp.finance.application.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record DebtOpeningLineResponse(
        Long id,
        int lineNo,
        Long partnerId,
        String partnerCode,
        String partnerName,
        String invoiceNo,
        LocalDate invoiceDate,
        LocalDate dueDate,
        BigDecimal amount,
        Long salesUserId,
        String note) {
}
