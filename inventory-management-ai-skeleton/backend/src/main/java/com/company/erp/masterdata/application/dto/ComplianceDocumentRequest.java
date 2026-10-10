package com.company.erp.masterdata.application.dto;

import com.company.erp.masterdata.domain.ComplianceOwnerType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

public record ComplianceDocumentRequest(
        @NotNull Long companyId,
        @NotNull Long documentTypeId,
        @NotNull ComplianceOwnerType ownerType,
        @NotNull Long ownerId,
        @NotBlank @Size(max = 100) String documentNo,
        LocalDate issuedDate,
        LocalDate expiryDate,
        @Size(max = 255) String issuer,
        @Size(max = 500) String note) {
}
