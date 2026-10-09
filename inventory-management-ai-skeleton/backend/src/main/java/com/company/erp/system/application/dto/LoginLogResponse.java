package com.company.erp.system.application.dto;

import com.company.erp.system.domain.LoginLog.LoginEvent;
import java.time.Instant;

public record LoginLogResponse(
        Long id,
        Instant occurredAt,
        LoginEvent event,
        String username,
        Long userId,
        String ipAddress,
        String userAgent) {
}
