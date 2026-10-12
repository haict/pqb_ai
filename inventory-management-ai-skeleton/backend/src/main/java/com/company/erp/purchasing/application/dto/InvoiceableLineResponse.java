package com.company.erp.purchasing.application.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Dòng phiếu nhận hàng đã nhập kho còn chưa nhận hóa đơn: số còn lại theo đơn vị của dòng, đơn giá tạm tính, thuế
 * suất của dòng đơn mua.
 */
public record InvoiceableLineResponse(
        Long receiptId,
        String receiptNo,
        LocalDate receiptDate,
        Long purchaseOrderId,
        String purchaseOrderNo,
        Long receiptLineId,
        int lineNo,
        Long orderLineId,
        Long productId,
        String productCode,
        String productName,
        Long unitId,
        BigDecimal remainingQuantity,
        BigDecimal unitPrice,
        Long taxRateId) {
}
