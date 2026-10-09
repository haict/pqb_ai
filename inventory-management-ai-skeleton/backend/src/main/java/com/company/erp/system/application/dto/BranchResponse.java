package com.company.erp.system.application.dto;

import com.company.erp.common.domain.RecordStatus;
import java.time.Instant;

public record BranchResponse(
        Long id,
        Long companyId,
        String code,
        String name,
        String address,
        String phone,
        RecordStatus status,
        int version,
        Instant updatedAt) {
}
