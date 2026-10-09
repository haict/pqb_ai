package com.company.erp.system.application.dto;

import com.company.erp.common.domain.RecordStatus;
import java.time.Instant;

public record DepartmentResponse(
        Long id,
        Long branchId,
        String code,
        String name,
        RecordStatus status,
        int version,
        Instant updatedAt) {
}
