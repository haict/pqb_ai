package com.company.erp.purchasing.application.dto;

import com.company.erp.common.domain.QualityStatus;
import java.math.BigDecimal;
import java.util.List;

/** Dòng trả hàng / giảm giá hàng mua. */
public record PurchaseReturnLineResponse(
        Long id,
        int lineNo,
        Long orderLineId,
        Long productId,
        String productCode,
        String productName,
        Long unitId,
        BigDecimal quantity,
        BigDecimal baseQuantity,
        String lotNo,
        QualityStatus qualityStatus,
        BigDecimal unitPrice,
        BigDecimal amount,
        BigDecimal taxRate,
        BigDecimal taxAmount,
        String description,
        List<String> serialNumbers) {
}
