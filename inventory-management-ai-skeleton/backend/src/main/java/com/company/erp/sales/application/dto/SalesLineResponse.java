package com.company.erp.sales.application.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Dòng đơn bán: giá, lô / serial chỉ định, hàng thực xuất khi ghi sổ ({@code items}), thông tin in biên bản giao nhận
 * (xuất xứ, model, số lưu hành), chỉ tiêu sau ghi sổ (đơn vị cơ sở: đã trả lại, còn trả được; giá trị đã giảm, còn
 * lại), tồn khả dụng và đang giữ tại kho của đơn (trước khi ghi sổ).
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
        String lotNo,
        String lotOverrideReason,
        List<String> serialNumbers,
        Long receiptTransactionId,
        String receiptNo,
        List<DeliveryItemResponse> items,
        String originCountryCode,
        String model,
        String registrationNo,
        BigDecimal returnedQuantity,
        BigDecimal returnableQuantity,
        BigDecimal adjustedAmount,
        BigDecimal remainingAmount,
        BigDecimal availableQuantity,
        BigDecimal reservedQuantity,
        String description) {
}
