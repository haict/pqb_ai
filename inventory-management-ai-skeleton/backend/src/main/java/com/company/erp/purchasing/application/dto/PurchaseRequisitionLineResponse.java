package com.company.erp.purchasing.application.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Dòng đề nghị mua; số đã đặt, đã đóng, còn lại theo đơn vị cơ sở. */
public record PurchaseRequisitionLineResponse(
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
        BigDecimal orderedQuantity,
        BigDecimal closedQuantity,
        BigDecimal remainingQuantity,
        Long suggestedSupplierId,
        String suggestedSupplierName,
        LocalDate requiredDate,
        String note) {
}
