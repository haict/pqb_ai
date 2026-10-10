package com.company.erp.masterdata.application.dto;

import com.company.erp.common.domain.RecordStatus;
import com.company.erp.masterdata.domain.ReferenceCategory;
import java.math.BigDecimal;
import java.time.Instant;

public record ReferenceItemResponse(
        Long id,
        Long companyId,
        ReferenceCategory category,
        String code,
        String name,
        BigDecimal numericValue,
        RecordStatus status,
        int version,
        Instant updatedAt) {
}
