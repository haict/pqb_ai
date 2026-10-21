package com.company.erp.system.application.dto;

import java.time.Instant;

public record DocumentTypeResponse(
        String code,
        String module,
        String name,
        int version,
        Instant updatedAt) {
}
