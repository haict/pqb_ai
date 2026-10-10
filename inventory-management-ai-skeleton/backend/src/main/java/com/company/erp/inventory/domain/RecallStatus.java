package com.company.erp.inventory.domain;

/** Trạng thái thu hồi (BR-INV-42): đã duyệt là đang thu hồi ({@code ACTIVE}), lô / serial bị khóa. */
public enum RecallStatus {
    DRAFT,
    SUBMITTED,
    ACTIVE,
    REJECTED,
    CLOSED,
    CANCELLED
}
