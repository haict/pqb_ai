package com.company.erp.masterdata.application.dto;

import java.math.BigDecimal;

public record PriceListItemResponse(
        Long id,
        Long priceListId,
        Long productId,
        String productCode,
        String productName,
        Long unitId,
        BigDecimal minQuantity,
        BigDecimal unitPrice) {
}
