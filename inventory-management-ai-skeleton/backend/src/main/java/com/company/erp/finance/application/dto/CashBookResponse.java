package com.company.erp.finance.application.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** Sổ quỹ / sổ tiền gửi của một quỹ trong khoảng ngày (BR-CSH-01); số liệu tính từ sổ quỹ. */
public record CashBookResponse(
        Long cashAccountId,
        String cashAccountCode,
        String cashAccountName,
        LocalDate dateFrom,
        LocalDate dateTo,
        BigDecimal openingBalance,
        BigDecimal totalIn,
        BigDecimal totalOut,
        BigDecimal closingBalance,
        List<CashBookLineResponse> lines) {
}
