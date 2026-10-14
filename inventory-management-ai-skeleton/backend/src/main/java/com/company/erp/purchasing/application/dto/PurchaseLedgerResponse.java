package com.company.erp.purchasing.application.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * Sổ chi tiết mua hàng (hiện trạng docs/HienTrang 14-15): danh sách dòng phát sinh theo ngày hạch toán và dòng tổng.
 * {@code generatedAt}: thời điểm lấy số liệu (BR-RPT-13).
 */
public record PurchaseLedgerResponse(
        String companyName,
        String companyAddress,
        LocalDate dateFrom,
        LocalDate dateTo,
        List<Line> lines,
        Totals total,
        Instant generatedAt) {

    /**
     * Một dòng. {@code documentType}: DMH (đơn mua), THN (trả hàng), GGM (giảm giá); {@code reversal}: dòng đảo (số
     * âm, ngày hạch toán là ngày đảo). Đơn mua: số lượng mua, đơn giá, giá trị mua (= số lượng × đơn giá), chiết khấu;
     * trả hàng: số lượng, giá trị trả lại; giảm giá: giá trị giảm giá.
     */
    public record Line(
            LocalDate postingDate,
            String documentType,
            Long documentId,
            String documentNo,
            LocalDate documentDate,
            LocalDate invoiceDate,
            String invoiceNo,
            Long productId,
            String productCode,
            String productName,
            String unitName,
            BigDecimal purchaseQuantity,
            BigDecimal unitPrice,
            BigDecimal purchaseAmount,
            BigDecimal discountAmount,
            BigDecimal returnQuantity,
            BigDecimal returnAmount,
            BigDecimal priceReductionAmount,
            boolean reversal) {
    }

    public record Totals(
            long lineCount,
            BigDecimal purchaseQuantity,
            BigDecimal purchaseAmount,
            BigDecimal discountAmount,
            BigDecimal returnQuantity,
            BigDecimal returnAmount,
            BigDecimal priceReductionAmount) {
    }
}
