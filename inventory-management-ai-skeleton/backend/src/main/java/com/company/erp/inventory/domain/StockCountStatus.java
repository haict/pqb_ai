package com.company.erp.inventory.domain;

/** Trạng thái kiểm kê (BR-INV-31); bị từ chối thì quay lại {@code COUNTING} để đếm lại. */
public enum StockCountStatus {
    DRAFT,
    COUNTING,
    SUBMITTED,
    APPROVED,
    POSTED,
    CANCELLED
}
