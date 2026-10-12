package com.company.erp.finance.application.dto;

import com.company.erp.finance.domain.DebtType;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Bảng đối chiếu công nợ một đối tượng (BR-AR-06): số dư đầu, phát sinh Nợ / Có theo chứng từ, số dư cuối (theo chiều
 * tự nhiên của loại công nợ), hóa đơn còn nợ và tiền trả trước tại ngày cuối. Số dư cuối = Σ còn nợ − trả trước.
 */
public record DebtStatementResponse(
        DebtType debtType,
        Long partnerId,
        String partnerCode,
        String partnerName,
        Long branchId,
        LocalDate dateFrom,
        LocalDate dateTo,
        BigDecimal openingBalance,
        BigDecimal totalDebit,
        BigDecimal totalCredit,
        BigDecimal closingBalance,
        BigDecimal openItemsAmount,
        BigDecimal prepaymentAmount,
        List<DebtLedgerLineResponse> lines,
        List<DebtItemResponse> openItems) {
}
