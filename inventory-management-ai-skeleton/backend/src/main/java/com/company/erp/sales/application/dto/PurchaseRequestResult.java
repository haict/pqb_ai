package com.company.erp.sales.application.dto;

/** Đề nghị mua đã lập từ đơn bán thiếu hàng (BR-SAL-15). */
public record PurchaseRequestResult(
        Long requisitionId,
        String requisitionNo,
        int lineCount) {
}
