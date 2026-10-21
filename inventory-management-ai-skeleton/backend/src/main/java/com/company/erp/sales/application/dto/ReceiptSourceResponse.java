package com.company.erp.sales.application.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Chứng từ nhập còn tồn để chọn cho dòng đơn bán (D-63, F8): dòng nhập theo lô / serial, số lượng theo đơn vị cơ sở;
 * {@code availableQuantity} là tồn ĐẠT hiện có của lô / serial tại kho (lô nhập nhiều lần dùng chung tồn).
 */
public record ReceiptSourceResponse(
        Long transactionId,
        String documentType,
        Long documentId,
        String documentNo,
        LocalDate postingDate,
        String lotNo,
        LocalDate expiryDate,
        String serialNo,
        BigDecimal receivedQuantity,
        BigDecimal unitCost,
        BigDecimal availableQuantity) {
}
