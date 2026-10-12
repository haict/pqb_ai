package com.company.erp.sales.application.dto;

import java.time.LocalDate;
import java.util.List;

/** Báo cáo doanh số theo nhóm và tổng cộng (khớp tổng các nhóm). */
public record SalesSummaryResponse(
        String groupBy,
        LocalDate dateFrom,
        LocalDate dateTo,
        List<SalesSummaryRow> rows,
        SalesSummaryRow total) {
}
