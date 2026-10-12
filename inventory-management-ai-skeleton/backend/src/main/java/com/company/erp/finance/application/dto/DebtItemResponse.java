package com.company.erp.finance.application.dto;

import com.company.erp.finance.domain.DebtItemStatus;
import com.company.erp.finance.domain.DebtType;
import java.math.BigDecimal;
import java.time.LocalDate;

/** Khoản công nợ; {@code overdueDays} tính tại ngày hiện tại theo lịch nghiệp vụ. */
public record DebtItemResponse(
        Long id,
        Long branchId,
        DebtType debtType,
        Long partnerId,
        String partnerCode,
        String partnerName,
        String sourceType,
        Long sourceId,
        String sourceNo,
        String invoiceNo,
        LocalDate documentDate,
        LocalDate dueDate,
        BigDecimal originalAmount,
        BigDecimal settledAmount,
        BigDecimal remainingAmount,
        long overdueDays,
        DebtItemStatus status) {
}
