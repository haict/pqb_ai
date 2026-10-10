package com.company.erp.masterdata.domain;

/** Loại kho ảo (BR-MD-41, D-19). */
public enum VirtualWarehouseKind {
    /** Hàng đang vận chuyển (chuyển kho khác chi nhánh, BR-INV-30). */
    IN_TRANSIT,
    /** Hàng gửi tại khách: máy đặt, cho mượn, ký gửi. */
    AT_CUSTOMER,
    /** Hàng đang sửa chữa. */
    IN_REPAIR,
    /** Hàng demo. */
    DEMO
}
