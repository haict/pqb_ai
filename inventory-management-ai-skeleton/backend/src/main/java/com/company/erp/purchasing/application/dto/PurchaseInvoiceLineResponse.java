package com.company.erp.purchasing.application.dto;

import java.math.BigDecimal;

/**
 * Dòng hóa đơn mua; {@code provisionalUnitPrice}: đơn giá tạm tính theo đơn mua (một đơn vị của dòng) để đối chiếu.
 */
public record PurchaseInvoiceLineResponse(
        Long id,
        int lineNo,
        Long receiptLineId,
        String receiptNo,
        Long orderLineId,
        Long productId,
        String productCode,
        String productName,
        Long unitId,
        BigDecimal conversionFactor,
        BigDecimal quantity,
        BigDecimal baseQuantity,
        BigDecimal unitPrice,
        BigDecimal provisionalUnitPrice,
        BigDecimal amount,
        Long taxRateId,
        BigDecimal taxRate,
        BigDecimal taxAmount,
        boolean promotional,
        String description) {
}
