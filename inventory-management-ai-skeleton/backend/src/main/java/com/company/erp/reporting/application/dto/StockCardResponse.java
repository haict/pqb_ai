package com.company.erp.reporting.application.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * Thẻ kho (BR-RPT-21) của một hàng, có thể lọc kho, lô, serial (lịch sử serial): tồn đầu, dòng nhập / xuất theo thứ tự
 * ghi sổ với tồn lũy kế, tồn cuối. Số lượng theo đơn vị cơ sở {@code unitName}.
 */
public record StockCardResponse(
        Long productId,
        String productCode,
        String productName,
        String unitName,
        LocalDate dateFrom,
        LocalDate dateTo,
        BigDecimal opening,
        List<Line> lines,
        BigDecimal totalIn,
        BigDecimal totalOut,
        BigDecimal closing,
        Instant generatedAt) {

    /**
     * {@code referenceType}, {@code referenceId}: phiếu kho (mở chi tiết chứng từ); {@code reversal}: dòng đảo;
     * {@code balance}: tồn sau dòng này.
     */
    public record Line(
            LocalDate postingDate,
            String transactionType,
            String referenceType,
            Long referenceId,
            String referenceNo,
            boolean reversal,
            String warehouseCode,
            String lotNo,
            LocalDate expiryDate,
            String serialNo,
            String partnerName,
            String qualityStatus,
            BigDecimal inQuantity,
            BigDecimal outQuantity,
            BigDecimal balance) {
    }
}
