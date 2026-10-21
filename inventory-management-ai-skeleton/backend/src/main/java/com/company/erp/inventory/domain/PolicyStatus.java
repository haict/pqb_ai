package com.company.erp.inventory.domain;

/**
 * Trạng thái chính sách xuất vượt tồn khả dụng; {@code APPROVED} = có hiệu lực. Không qua duyệt (D-61):
 * {@code SUBMITTED}, {@code REJECTED} chỉ còn ở chính sách lập trước D-61.
 */
public enum PolicyStatus {
    DRAFT,
    SUBMITTED,
    APPROVED,
    REJECTED,
    CANCELLED
}
