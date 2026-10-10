package com.company.erp.inventory.application.dto;

import com.company.erp.common.domain.QualityStatus;
import com.company.erp.inventory.domain.MovementDirection;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record InventoryLineResponse(
        Long id,
        int lineNo,
        Long productId,
        String productCode,
        String productName,
        Long unitId,
        BigDecimal conversionFactor,
        BigDecimal quantity,
        BigDecimal baseQuantity,
        MovementDirection direction,
        Long locationId,
        Long destinationLocationId,
        Long lotId,
        String lotNo,
        LocalDate manufactureDate,
        LocalDate expiryDate,
        QualityStatus qualityStatus,
        QualityStatus targetQualityStatus,
        BigDecimal unitCost,
        BigDecimal amount,
        String lotOverrideReason,
        String note,
        List<String> serialNumbers) {
}
