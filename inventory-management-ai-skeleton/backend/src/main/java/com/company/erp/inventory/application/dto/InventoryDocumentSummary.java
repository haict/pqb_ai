package com.company.erp.inventory.application.dto;

import com.company.erp.inventory.domain.InventoryDocumentStatus;
import com.company.erp.inventory.domain.InventoryDocumentType;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public record InventoryDocumentSummary(
        Long id,
        InventoryDocumentType documentType,
        String subType,
        String documentNo,
        LocalDate documentDate,
        LocalDate postingDate,
        InventoryDocumentStatus status,
        Long branchId,
        Long warehouseId,
        Long destinationWarehouseId,
        Long partnerId,
        BigDecimal totalAmount,
        int lineCount,
        Instant updatedAt) {
}
