package com.company.erp.inventory.application.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Cảnh báo tồn kho (BR-INV-40): {@code EXPIRING} hàng sắp hết hạn / đã hết hạn còn tồn; {@code BELOW_MIN} tồn dưới
 * định mức tối thiểu.
 */
public record StockAlertResponse(
        String alertType,
        Long productId,
        String productCode,
        String productName,
        Long warehouseId,
        Long lotId,
        String lotNo,
        LocalDate expiryDate,
        Long daysToExpiry,
        BigDecimal onHandQuantity,
        BigDecimal minQuantity) {
}
