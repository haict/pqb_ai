package com.company.erp.purchasing.application.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Dòng đơn mua với tiến độ (BR-PUR-05) theo đơn vị cơ sở: đã nhận, đã nhập kho, lỗi, đã nhận hóa đơn, đã đóng, còn
 * thiếu (chưa nhập kho, chưa đóng).
 */
public record PurchaseOrderLineResponse(
        Long id,
        int lineNo,
        Long productId,
        String productCode,
        String productName,
        Long unitId,
        Long baseUnitId,
        BigDecimal conversionFactor,
        BigDecimal quantity,
        BigDecimal baseQuantity,
        BigDecimal unitPrice,
        BigDecimal discountPercent,
        BigDecimal discountAmount,
        BigDecimal amount,
        Long taxRateId,
        BigDecimal taxRate,
        BigDecimal taxAmount,
        LocalDate expectedDate,
        Long requisitionLineId,
        BigDecimal receivedQuantity,
        BigDecimal stockedQuantity,
        BigDecimal rejectedQuantity,
        BigDecimal invoicedQuantity,
        BigDecimal closedQuantity,
        BigDecimal outstandingQuantity,
        String note) {
}
