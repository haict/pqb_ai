package com.company.erp.finance.application.dto;

import com.company.erp.finance.domain.DebtType;
import com.company.erp.finance.domain.ReconciliationStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/** Biên bản đối chiếu công nợ; {@code difference} = số xác nhận − số hệ thống. */
public record DebtReconciliationResponse(
        Long id,
        Long companyId,
        Long branchId,
        String documentNo,
        DebtType debtType,
        Long partnerId,
        String partnerCode,
        String partnerName,
        LocalDate periodFrom,
        LocalDate asOfDate,
        BigDecimal systemBalance,
        BigDecimal confirmedBalance,
        BigDecimal difference,
        ReconciliationStatus status,
        String contactName,
        Instant confirmedAt,
        String note,
        Long createdBy,
        Instant createdAt,
        int version) {
}
