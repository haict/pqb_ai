package com.company.erp.inventory.domain;

/**
 * Trạng thái thu hồi (BR-INV-42): kích hoạt là đang thu hồi ({@code ACTIVE}), lô / serial bị khóa. Không qua duyệt
 * (D-61): {@code SUBMITTED}, {@code REJECTED} chỉ còn ở phiếu lập trước D-61.
 */
public enum RecallStatus {
    DRAFT,
    SUBMITTED,
    ACTIVE,
    REJECTED,
    CLOSED,
    CANCELLED
}
