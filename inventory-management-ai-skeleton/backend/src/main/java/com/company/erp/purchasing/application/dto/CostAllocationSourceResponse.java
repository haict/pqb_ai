package com.company.erp.purchasing.application.dto;

import java.math.BigDecimal;

/** Dòng chi phí của phân bổ; {@code remainingAmount} chỉ có ở danh sách dòng chi phí còn phân bổ được. */
public record CostAllocationSourceResponse(
        Integer lineNo,
        Long invoiceId,
        String invoiceDocumentNo,
        String invoiceLabel,
        String supplierName,
        Long invoiceLineId,
        Long productId,
        String productCode,
        String productName,
        BigDecimal amount,
        BigDecimal remainingAmount) {
}
