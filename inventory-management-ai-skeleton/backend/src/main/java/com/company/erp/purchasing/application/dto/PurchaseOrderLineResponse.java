package com.company.erp.purchasing.application.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Dòng đơn mua với lô / hạn dùng / serial và chỉ tiêu sau ghi sổ: đã trả NCC (đơn vị cơ sở), giá trị đã giảm (trả hàng
 * + giảm giá), chi phí đã phân bổ (dòng dịch vụ), còn trả được, giá trị còn lại.
 */
public record PurchaseOrderLineResponse(
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
        BigDecimal unitPrice,
        BigDecimal discountPercent,
        BigDecimal discountAmount,
        BigDecimal amount,
        Long taxRateId,
        BigDecimal taxRate,
        BigDecimal taxAmount,
        boolean serviceLine,
        Long locationId,
        String lotNo,
        LocalDate manufactureDate,
        LocalDate expiryDate,
        List<String> serialNumbers,
        BigDecimal returnedQuantity,
        BigDecimal adjustedAmount,
        BigDecimal allocatedAmount,
        BigDecimal returnableQuantity,
        BigDecimal remainingAmount,
        String note) {
}
