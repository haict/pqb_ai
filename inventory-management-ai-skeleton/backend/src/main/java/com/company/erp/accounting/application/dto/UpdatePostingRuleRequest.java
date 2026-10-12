package com.company.erp.accounting.application.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

public record UpdatePostingRuleRequest(
        Long debitAccountId,
        Long creditAccountId,
        LocalDate validTo,
        @Size(max = 500) String description,
        @NotNull Integer version) {
}
