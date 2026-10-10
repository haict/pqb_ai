package com.company.erp.inventory.application.dto;

import com.company.erp.common.domain.QualityStatus;
import java.math.BigDecimal;
import java.time.LocalDate;

public record StockCountLineResponse(
        Long id,
        Long productId,
        String productCode,
        String productName,
        Long locationId,
        Long lotId,
        String lotNo,
        LocalDate expiryDate,
        Long serialId,
        String serialNo,
        QualityStatus qualityStatus,
        BigDecimal snapshotQuantity,
        BigDecimal countedQuantity,
        BigDecimal difference,
        String note) {
}
