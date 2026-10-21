package com.company.erp.inventory.domain;

/** Trạng thái kiểm kê (BR-INV-31); {@code SUBMITTED}, {@code APPROVED} chỉ còn ở phiếu lập trước D-61. */
public enum StockCountStatus {
    DRAFT,
    COUNTING,
    SUBMITTED,
    APPROVED,
    POSTED,
    CANCELLED
}
