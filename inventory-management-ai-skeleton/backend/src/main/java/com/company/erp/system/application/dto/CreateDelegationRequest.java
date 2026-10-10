package com.company.erp.system.application.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;

/**
 * Ủy quyền duyệt. {@code delegatorUserId} trống: người đang đăng nhập ủy quyền của mình; ủy quyền thay người khác
 * cần quyền {@code approval-workflow:manage}. {@code documentType} trống: mọi loại chứng từ. {@code validFrom} trống:
 * hiệu lực ngay.
 */
public record CreateDelegationRequest(
        Long delegatorUserId,
        @NotNull Long delegateUserId,
        @Size(max = 30) String documentType,
        Instant validFrom,
        @NotNull Instant validTo,
        @Size(max = 500) String reason) {
}
