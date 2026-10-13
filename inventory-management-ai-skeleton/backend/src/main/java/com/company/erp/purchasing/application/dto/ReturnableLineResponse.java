package com.company.erp.purchasing.application.dto;

import com.company.erp.common.domain.QualityStatus;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Dòng đơn mua đã ghi sổ còn trả / giảm giá được. Số lượng theo đơn vị của dòng đơn mua (dòng dịch vụ: 0, chỉ giảm
 * giá); {@code unitPrice}: đơn giá sau chiết khấu; {@code remainingAmount}: giá trị còn giảm được.
 */
public record ReturnableLineResponse(
        Long orderId,
        String orderNo,
        LocalDate postingDate,
        String invoiceLabel,
        Long orderLineId,
        Long warehouseId,
        boolean serviceLine,
        Long productId,
        String productCode,
        String productName,
        Long unitId,
        String lotNo,
        LocalDate expiryDate,
        QualityStatus qualityStatus,
        BigDecimal remainingQuantity,
        BigDecimal unitPrice,
        BigDecimal taxRate,
        BigDecimal remainingAmount,
        List<String> serialNumbers) {
}
