package com.company.erp.accounting.domain;

import java.math.BigDecimal;

/** Một vế bút toán đã xác định tài khoản, trước khi ghi. {@code debit = true} là vế Nợ. */
public record JournalLineDraft(
        Long accountId,
        boolean debit,
        BigDecimal amount,
        String transactionCode,
        Long postingRuleId,
        Long partnerId,
        Long productId,
        Long warehouseId,
        Long departmentId,
        Long employeeId,
        Long sourceLineId,
        String description) {
}
