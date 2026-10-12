package com.company.erp.accounting.application.dto;

import com.company.erp.accounting.domain.JournalEntryKind;
import com.company.erp.accounting.domain.JournalEntryStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public record JournalEntrySummaryResponse(
        Long id,
        Long companyId,
        Long branchId,
        String entryNo,
        LocalDate postingDate,
        JournalEntryKind entryKind,
        String sourceType,
        Long sourceId,
        String sourceNo,
        String description,
        BigDecimal totalAmount,
        JournalEntryStatus status,
        Long reversalOfId,
        Instant createdAt) {
}
