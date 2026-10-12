package com.company.erp.sales.application.dto;

import com.company.erp.common.domain.QualityStatus;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** Dòng trả hàng / giảm giá hàng bán. */
public record SalesReturnLineResponse(
        Long id,
        int lineNo,
        Long deliveryLineId,
        String deliveryNo,
        Long invoiceLineId,
        Long orderLineId,
        Long productId,
        String productCode,
        String productName,
        Long unitId,
        BigDecimal quantity,
        BigDecimal baseQuantity,
        String lotNo,
        LocalDate expiryDate,
        QualityStatus qualityStatus,
        BigDecimal unitPrice,
        BigDecimal amount,
        BigDecimal taxRate,
        BigDecimal taxAmount,
        String description,
        List<String> serialNumbers) {
}
