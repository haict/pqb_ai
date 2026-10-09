package com.company.erp.system.application.dto;

import com.company.erp.common.domain.RecordStatus;
import java.time.Instant;

public record CompanyResponse(
        Long id,
        String code,
        String name,
        String taxCode,
        String address,
        String phone,
        String email,
        RecordStatus status,
        int version,
        Instant updatedAt) {
}
