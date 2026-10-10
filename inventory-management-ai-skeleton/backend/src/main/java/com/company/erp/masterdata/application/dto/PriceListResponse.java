package com.company.erp.masterdata.application.dto;

import com.company.erp.common.domain.RecordStatus;
import com.company.erp.masterdata.domain.PriceTargetType;
import com.company.erp.masterdata.domain.PriceType;
import java.time.Instant;
import java.time.LocalDate;

public record PriceListResponse(
        Long id,
        Long companyId,
        String code,
        String name,
        PriceType priceType,
        PriceTargetType targetType,
        Long partnerGroupId,
        Long partnerId,
        String currencyCode,
        LocalDate validFrom,
        LocalDate validTo,
        RecordStatus status,
        int version,
        Instant updatedAt) {
}
