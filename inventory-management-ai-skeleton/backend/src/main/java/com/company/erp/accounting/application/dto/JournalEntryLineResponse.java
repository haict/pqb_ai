package com.company.erp.accounting.application.dto;

import java.math.BigDecimal;

public record JournalEntryLineResponse(
        Long id,
        int lineNo,
        Long accountId,
        String accountCode,
        String accountName,
        BigDecimal debitAmount,
        BigDecimal creditAmount,
        String transactionCode,
        Long postingRuleId,
        Long partnerId,
        String partnerCode,
        String partnerName,
        Long productId,
        Long warehouseId,
        Long departmentId,
        Long employeeId,
        Long sourceLineId,
        String description) {
}
