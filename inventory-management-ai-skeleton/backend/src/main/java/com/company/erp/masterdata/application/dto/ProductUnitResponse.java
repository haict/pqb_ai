package com.company.erp.masterdata.application.dto;

import com.company.erp.common.domain.RecordStatus;
import java.math.BigDecimal;
import java.time.LocalDate;

public record ProductUnitResponse(
        Long id,
        Long unitId,
        BigDecimal conversionFactor,
        LocalDate effectiveFrom,
        RecordStatus status,
        int version) {
}
