package com.company.erp.sales.domain;

/** Trạng thái giao hàng (BR-SAL-23): chờ giao (đã xuất kho), đang giao, đã giao (khách nhận), giao thất bại. */
public enum DeliveryStatus {
    PENDING,
    IN_TRANSIT,
    DELIVERED,
    FAILED
}
