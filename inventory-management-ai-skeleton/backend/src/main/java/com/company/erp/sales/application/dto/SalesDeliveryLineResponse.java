package com.company.erp.sales.application.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * Dòng phiếu giao; {@code items} là hàng thực xuất (sau khi xuất kho). Số lưu hành, xuất xứ cho biên bản giao nhận
 * (BR-SAL-22).
 */
public record SalesDeliveryLineResponse(
        Long id,
        int lineNo,
        Long orderLineId,
        Long productId,
        String productCode,
        String productName,
        Long unitId,
        BigDecimal conversionFactor,
        BigDecimal quantity,
        BigDecimal baseQuantity,
        String lotNo,
        String lotOverrideReason,
        BigDecimal invoicedQuantity,
        BigDecimal returnedQuantity,
        BigDecimal uninvoicedQuantity,
        String registrationNo,
        String originCountryCode,
        String note,
        List<String> serialNumbers,
        List<DeliveryItemResponse> items) {
}
