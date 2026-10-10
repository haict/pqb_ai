package com.company.erp.inventory.application.dto;

import java.math.BigDecimal;

/**
 * Bốn chỉ tiêu tồn theo (hàng, kho) (BR mục 5.4, BR-GEN-30): thực tế, bán được (ĐẠT, chưa hết hạn, không khóa),
 * giữ chỗ, khả dụng = bán được − giữ chỗ, đang về.
 */
public record StockSummaryResponse(
        Long productId,
        String productCode,
        String productName,
        Long baseUnitId,
        Long warehouseId,
        BigDecimal onHandQuantity,
        BigDecimal sellableQuantity,
        BigDecimal reservedQuantity,
        BigDecimal availableQuantity,
        BigDecimal incomingQuantity) {
}
