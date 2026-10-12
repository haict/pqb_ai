package com.company.erp.sales.application.dto;

import java.math.BigDecimal;

/** Dòng hóa đơn bán. */
public record SalesInvoiceLineResponse(
        Long id,
        int lineNo,
        Long deliveryLineId,
        String deliveryNo,
        Long orderLineId,
        String orderNo,
        Long productId,
        String productCode,
        String productName,
        Long unitId,
        BigDecimal quantity,
        BigDecimal baseQuantity,
        BigDecimal unitPrice,
        BigDecimal discountPercent,
        BigDecimal discountAmount,
        BigDecimal amount,
        BigDecimal taxRate,
        BigDecimal taxAmount,
        boolean promotional,
        BigDecimal returnedQuantity,
        BigDecimal adjustedAmount,
        String description) {
}
