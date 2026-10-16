package com.company.erp.reporting.application.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * Nhập - xuất - tồn theo kỳ (BR-RPT-22) theo hàng × kho, số lượng đơn vị cơ sở. Chưa có giá vốn
 * ({@code valueAvailable = false}, Giai đoạn 8): giá trị là tạm tính ({@code valueProvisional = true}) = số lượng ×
 * đơn giá nhập bình quân của hàng đến hết kỳ ({@code averageUnitCost}), làm tròn đến đồng; hàng chưa có đơn giá nhập
 * thì giá trị trống.
 */
public record InventoryMovementResponse(
        LocalDate dateFrom,
        LocalDate dateTo,
        List<Row> rows,
        boolean valueAvailable,
        boolean valueProvisional,
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
            BigDecimal closing,
            BigDecimal averageUnitCost,
            BigDecimal openingValue,
            BigDecimal inValue,
            BigDecimal outValue,
            BigDecimal closingValue) {
    }
}
