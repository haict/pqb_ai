package com.company.erp.accounting.application.dto;

import java.util.List;

/** Bút toán kèm dòng; {@code reversedBy} là bút toán đảo (nếu đã đảo). */
public record JournalEntryResponse(
        JournalEntrySummaryResponse entry,
        List<JournalEntryLineResponse> lines,
        JournalEntrySummaryResponse reversedBy) {
}
