package com.company.erp.accounting.application.dto;

import com.company.erp.common.domain.RecordStatus;
import java.time.Instant;
import java.time.LocalDate;

/** Quy tắc hạch toán; tài khoản để trống nghĩa là chứng từ truyền tài khoản (ADR-11). */
public record PostingRuleResponse(
        Long id,
        Long companyId,
        String documentType,
        String transactionCode,
        String productType,
        String partnerRole,
        Long debitAccountId,
        String debitAccountCode,
        Long creditAccountId,
        String creditAccountCode,
        LocalDate validFrom,
        LocalDate validTo,
        String description,
        RecordStatus status,
        int version,
        Instant updatedAt) {
}
