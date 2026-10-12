package com.company.erp.finance.application.dto;

import com.company.erp.finance.domain.DebtType;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Sổ chi tiết công nợ của một đối tượng trong kỳ: số dư đầu, phát sinh Nợ / Có, số dư cuối. Số dư tính từ sổ chi tiết
 * (BR-AR-01, nguyên tắc 12.1); dương nghĩa là còn phải thu (phải thu) hoặc còn phải trả (phải trả).
 */
public record DebtLedgerResponse(
        DebtType debtType,
        Long partnerId,
        String partnerCode,
        String partnerName,
        LocalDate dateFrom,
        LocalDate dateTo,
        BigDecimal openingBalance,
        BigDecimal totalDebit,
        BigDecimal totalCredit,
        BigDecimal closingBalance,
        List<DebtLedgerLineResponse> lines) {
}
