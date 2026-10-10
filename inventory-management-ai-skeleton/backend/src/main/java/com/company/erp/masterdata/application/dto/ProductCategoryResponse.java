package com.company.erp.masterdata.application.dto;

import com.company.erp.common.domain.RecordStatus;
import java.time.Instant;

public record ProductCategoryResponse(
        Long id,
        Long companyId,
        Long parentId,
        String code,
        String name,
        RecordStatus status,
        int version,
        Instant updatedAt) {
}
