package com.company.erp.sales.application.dto;

import com.company.erp.sales.domain.DeliveryStatus;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Dòng còn xuất hóa đơn được: dòng phiếu giao đã xuất kho ({@code deliveryLineId}) hoặc dòng dịch vụ của đơn bán
 * đã duyệt. Số lượng còn lại theo đơn vị của dòng.
 */
public record InvoiceableSalesLineResponse(
        Long deliveryId,
        String deliveryNo,
        LocalDate deliveryDate,
        DeliveryStatus deliveryStatus,
        Long deliveryLineId,
        Long orderId,
        String orderNo,
        Long orderLineId,
        Long productId,
        String productCode,
        String productName,
        Long unitId,
        BigDecimal remainingQuantity,
        BigDecimal unitPrice,
        BigDecimal discountPercent,
        BigDecimal taxRate,
        boolean serviceLine) {
}
