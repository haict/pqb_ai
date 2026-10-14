package com.company.erp.reporting.application.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * Nhập - xuất - tồn theo kỳ (BR-RPT-22) theo hàng × kho, số lượng đơn vị cơ sở. Giá trị (tiền) chưa có vì chưa tính
 * giá vốn ({@code valueAvailable = false}, Giai đoạn 8). Không cộng tổng số lượng vì các hàng khác đơn vị tính.
 */
public record InventoryMovementResponse(
        LocalDate dateFrom,
        LocalDate dateTo,
        List<Row> rows,
        boolean valueAvailable,
        Instant generatedAt) {

    public record Row(
            Long productId,
            String productCode,
            String productName,
            String unitName,
            Long warehouseId,
            String warehouseCode,
            String warehouseName,
            BigDecimal opening,
            BigDecimal inQuantity,
            BigDecimal outQuantity,
            BigDecimal closing) {
    }
}
