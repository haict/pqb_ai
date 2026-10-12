package com.company.erp.sales.application.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Dòng báo giá / đơn bán. Tiến độ (đơn vị cơ sở: đã xuất, đã giao, đã xuất hóa đơn, đã trả lại, đã đóng, còn phải
 * xuất) và tồn khả dụng, đang giữ tại kho của đơn chỉ có ở đơn bán.
 */
public record SalesLineResponse(
        Long id,
        int lineNo,
        Long productId,
        String productCode,
        String productName,
        Long unitId,
        Long baseUnitId,
        BigDecimal conversionFactor,
        BigDecimal quantity,
        BigDecimal baseQuantity,
        BigDecimal listPrice,
        BigDecimal floorPrice,
        String priceSource,
        BigDecimal unitPrice,
        BigDecimal discountPercent,
        BigDecimal discountAmount,
        BigDecimal amount,
        Long taxRateId,
        BigDecimal taxRate,
        BigDecimal taxAmount,
        boolean promotional,
        boolean serviceLine,
        LocalDate expectedDate,
        BigDecimal issuedQuantity,
        BigDecimal deliveredQuantity,
        BigDecimal invoicedQuantity,
        BigDecimal returnedQuantity,
        BigDecimal closedQuantity,
        BigDecimal outstandingQuantity,
        BigDecimal availableQuantity,
        BigDecimal reservedQuantity,
        String description) {
}
