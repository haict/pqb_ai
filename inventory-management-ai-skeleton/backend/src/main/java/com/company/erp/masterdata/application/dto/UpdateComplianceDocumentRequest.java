package com.company.erp.masterdata.application.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

public record UpdateComplianceDocumentRequest(
        @NotBlank @Size(max = 100) String documentNo,
        LocalDate issuedDate,
        LocalDate expiryDate,
        @Size(max = 255) String issuer,
        @Size(max = 500) String note,
        @NotNull Integer version) {
}
