package com.company.erp.purchasing.application.dto;

import java.math.BigDecimal;

/** Dòng phiếu nhận nhận phân bổ: cơ sở phân bổ (số lượng cơ sở, giá trị tạm tính) và số tiền được phân bổ. */
public record CostAllocationTargetResponse(
        int lineNo,
        Long receiptId,
        String receiptNo,
        Long receiptLineId,
        Long productId,
        String productCode,
        String productName,
        BigDecimal baseQuantity,
        BigDecimal basisValue,
        BigDecimal allocatedAmount) {
}
