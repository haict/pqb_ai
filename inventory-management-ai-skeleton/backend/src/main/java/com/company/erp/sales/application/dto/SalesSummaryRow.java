package com.company.erp.sales.application.dto;

import java.math.BigDecimal;

/**
 * Một nhóm của báo cáo doanh số (BR-SAL-51): doanh thu theo hóa đơn bán đã ghi sổ, giảm trừ (trả hàng, giảm giá đã
 * ghi sổ), doanh thu thuần, thuế; công nợ phải thu còn lại (nhóm khách / nhân viên / chi nhánh). Lợi nhuận gộp cần giá
 * vốn (Giai đoạn 8).
 */
public record SalesSummaryRow(
        Long key,
        String code,
        String name,
        long invoiceCount,
        BigDecimal revenue,
        BigDecimal deductions,
        BigDecimal netRevenue,
        BigDecimal taxAmount,
        BigDecimal receivable) {
}
