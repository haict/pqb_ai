package com.company.erp.common.application;

import java.util.Objects;

/**
 * Nội dung một thao tác cần ghi nhật ký.
 *
 * @param actorUserId người thực hiện; {@code null} chỉ cho tác vụ hệ thống
 * @param action      ví dụ {@code POST}, {@code REVERSE}, {@code PRICE_CHANGE}
 * @param entityType  ví dụ {@code INVENTORY_ISSUE}
 * @param before      trạng thái trước (được chuyển sang JSON), có thể {@code null}
 * @param after       trạng thái sau (được chuyển sang JSON), có thể {@code null}
 */
public record AuditEntry(
        Long actorUserId,
        String action,
        String entityType,
        Long entityId,
        String documentNo,
        String reason,
        Object before,
        Object after,
        Long companyId,
        Long branchId) {

    public AuditEntry {
        Objects.requireNonNull(action, "action");
        Objects.requireNonNull(entityType, "entityType");
    }
}
