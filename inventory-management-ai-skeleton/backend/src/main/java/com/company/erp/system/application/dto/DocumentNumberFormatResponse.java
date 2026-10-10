package com.company.erp.system.application.dto;

import com.company.erp.common.domain.NumberResetCycle;
import java.time.LocalDate;

public record DocumentNumberFormatResponse(
        Long id,
        Long companyId,
        String documentType,
        String prefix,
        int sequenceLength,
        NumberResetCycle resetCycle,
        LocalDate effectiveFrom,
        DocumentNumberFormatStatus status,
        int version) {
}
