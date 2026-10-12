package com.company.erp.sales.application.dto;

import com.company.erp.sales.domain.SalesDocumentStatus;

/** Kết quả bán nhanh: các chứng từ đã lập (trống nếu dừng trước bước đó) và thông báo. */
public record QuickSaleResponse(
        Long orderId,
        String orderNo,
        SalesDocumentStatus orderStatus,
        Long deliveryId,
        String deliveryNo,
        Long invoiceId,
        String invoiceNo,
        String message) {
}
