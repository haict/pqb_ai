package com.company.erp.masterdata.application.dto;

import com.company.erp.common.domain.RecordStatus;
import java.time.Instant;

public record CatalogItemResponse(
        Long id,
        Long companyId,
        String code,
        String name,
        String countryCode,
        RecordStatus status,
        int version,
        Instant updatedAt) {
}
