package com.company.erp.sales.application.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Dòng đơn bán đã ghi sổ còn trả / giảm giá được, chia theo lô đã xuất; số lượng theo đơn vị của dòng (dòng dịch vụ:
 * 0, chỉ giảm giá); serial đã xuất; {@code remainingAmount}: giá trị còn giảm được của dòng.
 */
public record ReturnableSalesLineResponse(
        Long orderId,
        String orderNo,
        LocalDate postingDate,
        String invoiceLabel,
        Long warehouseId,
        Long orderLineId,
        boolean serviceLine,
        Long productId,
        String productCode,
        String productName,
        Long unitId,
        String lotNo,
        LocalDate expiryDate,
        BigDecimal remainingQuantity,
        BigDecimal unitPrice,
        BigDecimal taxRate,
        BigDecimal remainingAmount,
        List<String> serialNumbers) {
}
