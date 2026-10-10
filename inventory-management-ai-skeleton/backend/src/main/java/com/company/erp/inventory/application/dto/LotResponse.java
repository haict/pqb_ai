package com.company.erp.inventory.application.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Lô kèm tổng tồn hiện tại trên các kho trong phạm vi. */
public record LotResponse(
        Long id,
        Long productId,
        String productCode,
        String productName,
        String lotNo,
        LocalDate manufactureDate,
        LocalDate expiryDate,
        Long daysToExpiry,
        boolean blocked,
        String blockReason,
        BigDecimal onHandQuantity,
        int version) {
}
