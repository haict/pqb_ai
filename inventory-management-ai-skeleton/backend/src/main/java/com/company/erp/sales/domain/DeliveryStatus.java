package com.company.erp.sales.domain;

/**
 * Trạng thái giao hàng của đơn bán đã ghi sổ (BR-SAL-22, D-54): chờ giao (đã xuất kho), đang giao, đã giao (khách
 * nhận). Giao thất bại thì đảo đơn bán (hàng về kho).
 */
public enum DeliveryStatus {
    PENDING,
    IN_TRANSIT,
    DELIVERED
}
