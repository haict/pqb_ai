package com.company.erp.system.application.dto;

import com.company.erp.common.domain.PeriodModule;
import java.time.Instant;

/**
 * Trạng thái khóa của một phân hệ; {@code changedAt}, {@code changedBy}, {@code reason} là lần khóa/mở
 * gần nhất ({@code null} khi chưa từng khóa).
 */
public record PeriodLockStatus(
        PeriodModule module,
        boolean locked,
        Instant changedAt,
        String changedBy,
        String reason) {
}
