package com.company.erp.accounting.application.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

public record PostingRuleRequest(
        @NotNull Long companyId,
        @NotBlank @Size(max = 30) String documentType,
        @NotBlank @Pattern(regexp = "[A-Z0-9_]{1,50}", message = "Mã nghiệp vụ gồm 1-50 ký tự A-Z, 0-9, _")
        String transactionCode,
        @Size(max = 30) String productType,
        @Size(max = 30) String partnerRole,
        Long debitAccountId,
        Long creditAccountId,
        @NotNull LocalDate validFrom,
        LocalDate validTo,
        @Size(max = 500) String description) {
}
