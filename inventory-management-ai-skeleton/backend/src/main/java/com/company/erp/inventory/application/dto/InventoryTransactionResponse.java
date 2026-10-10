package com.company.erp.inventory.application.dto;

import com.company.erp.common.domain.QualityStatus;
import com.company.erp.inventory.domain.MovementDirection;
import com.company.erp.inventory.domain.TransactionType;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/** Dòng sổ nhật ký kho kèm số lô, serial, tên hàng để hiển thị và truy vết (BR-INV-06). */
public record InventoryTransactionResponse(
        Long id,
        LocalDate postingDate,
        Instant postedAt,
        Long productId,
        String productCode,
        String productName,
        Long warehouseId,
        Long locationId,
        Long lotId,
        String lotNo,
        LocalDate expiryDate,
        Long serialId,
        String serialNo,
        QualityStatus qualityStatus,
        TransactionType transactionType,
        MovementDirection direction,
        BigDecimal quantity,
        BigDecimal unitCost,
        BigDecimal amount,
        Long partnerId,
        String referenceType,
        Long referenceId,
        String referenceNo,
        Long reversalOfId) {
}
