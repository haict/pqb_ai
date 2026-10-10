package com.company.erp.inventory.domain;

/** Trạng thái chứng từ kho nhóm A (BR mục 5.1); {@code IN_TRANSIT} cho chuyển kho khác chi nhánh (BR-INV-30). */
public enum InventoryDocumentStatus {
    DRAFT,
    SUBMITTED,
    APPROVED,
    REJECTED,
    IN_TRANSIT,
    POSTED,
    CANCELLED,
    REVERSED
}
