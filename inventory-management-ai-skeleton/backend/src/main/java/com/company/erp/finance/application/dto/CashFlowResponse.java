package com.company.erp.finance.application.dto;

import com.company.erp.finance.domain.CashAccountType;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Dòng tiền thực tế (BR-CSH-06, phần thực tế) trong khoảng ngày: theo quỹ (đầu kỳ, vào, ra, cuối kỳ) và theo nội dung
 * thu chi. Chuyển tiền nội bộ xuất hiện ở cả hai quỹ, tổng toàn công ty triệt tiêu.
 */
public record CashFlowResponse(
        LocalDate dateFrom,
        LocalDate dateTo,
        List<AccountRow> accounts,
        List<PurposeRow> purposes,
        BigDecimal openingBalance,
        BigDecimal totalIn,
        BigDecimal totalOut,
        BigDecimal closingBalance) {

    public record AccountRow(Long cashAccountId, String code, String name, CashAccountType accountType,
                             Long branchId, BigDecimal openingBalance, BigDecimal inAmount, BigDecimal outAmount,
                             BigDecimal closingBalance) {
    }

    public record PurposeRow(String transactionCode, BigDecimal inAmount, BigDecimal outAmount) {
    }
}
