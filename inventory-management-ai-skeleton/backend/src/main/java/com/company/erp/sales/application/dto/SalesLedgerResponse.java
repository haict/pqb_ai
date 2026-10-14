package com.company.erp.sales.application.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * Sổ chi tiết bán hàng (mẫu S16-DNN, BR-RPT-26): mỗi hàng hóa một khối dòng phát sinh và dòng tổng; nhóm ngoài theo
 * "Thống kê theo" (một nhóm duy nhất khi {@code groupBy = NONE}). Giá vốn và lãi gộp {@code null} khi chưa tính giá
 * vốn ({@code costAvailable = false}, Giai đoạn 8). {@code generatedAt}: thời điểm lấy số liệu (BR-RPT-13).
 */
public record SalesLedgerResponse(
        String companyName,
        String companyAddress,
        LocalDate dateFrom,
        LocalDate dateTo,
        String groupBy,
        List<Group> groups,
        Totals total,
        boolean costAvailable,
        Instant generatedAt) {

    /** Nhóm ngoài: khách hàng hoặc nhóm hàng; {@code key = null} khi không nhóm hoặc hàng chưa phân nhóm. */
    public record Group(Long key, String code, String name, List<ProductBlock> products, Totals totals) {
    }

    public record ProductBlock(Long productId, String productCode, String productName, List<Line> lines,
                               Totals totals) {
    }

    /**
     * Một dòng phát sinh. {@code documentType}: DBH (đơn bán), TLB (trả lại), GGB (giảm giá); {@code reversal}: dòng
     * đảo (số âm, ngày hạch toán là ngày đảo). Doanh thu: {@code quantity}, {@code unitPrice}, {@code amount}; khoản
     * giảm trừ: {@code taxDeduction}, {@code otherDeduction} (trả lại, giảm giá).
     */
    public record Line(
            LocalDate postingDate,
            String documentType,
            Long documentId,
            String documentNo,
            LocalDate documentDate,
            LocalDate invoiceDate,
            String invoiceNo,
            String description,
            String offsetAccount,
            String unitName,
            BigDecimal quantity,
            BigDecimal unitPrice,
            BigDecimal amount,
            BigDecimal taxDeduction,
            BigDecimal otherDeduction,
            boolean reversal) {
    }

    /** Cộng số phát sinh, doanh thu thuần = doanh thu − giảm trừ; giá vốn, lãi gộp {@code null} khi chưa có giá vốn. */
    public record Totals(
            BigDecimal quantity,
            BigDecimal amount,
            BigDecimal taxDeduction,
            BigDecimal otherDeduction,
            BigDecimal netRevenue,
            BigDecimal costOfGoodsSold,
            BigDecimal grossProfit) {
    }
}
