package com.company.erp.purchasing.application.dto;

import java.math.BigDecimal;

/** Dòng hàng hóa của đơn mua nhận phân bổ: cơ sở phân bổ (số lượng cơ sở, giá trị nhập) và số tiền được phân bổ. */
public record CostAllocationTargetResponse(
        int lineNo,
        Long orderId,
        String orderNo,
        Long orderLineId,
        Long productId,
        String productCode,
        String productName,
        BigDecimal baseQuantity,
        BigDecimal basisValue,
        BigDecimal allocatedAmount) {
}
