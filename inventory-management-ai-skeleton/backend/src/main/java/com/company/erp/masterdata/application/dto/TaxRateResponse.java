package com.company.erp.masterdata.application.dto;

import com.company.erp.common.domain.RecordStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public record TaxRateResponse(
        Long id,
        Long companyId,
        String code,
        String name,
        BigDecimal rate,
        LocalDate validFrom,
        LocalDate validTo,
        RecordStatus status,
        int version,
        Instant updatedAt) {
}
