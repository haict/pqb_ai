package com.company.erp.sales.application.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Dòng còn trả / giảm giá được: theo (dòng phiếu giao, lô) khi chưa có hóa đơn hoặc theo dòng hóa đơn; số lượng theo
 * đơn vị của dòng; serial đã xuất; {@code remainingAmount}: giá trị còn giảm được của dòng hóa đơn.
 */
public record ReturnableSalesLineResponse(
        Long deliveryId,
        String deliveryNo,
        Long deliveryLineId,
        Long invoiceLineId,
        Long orderLineId,
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
