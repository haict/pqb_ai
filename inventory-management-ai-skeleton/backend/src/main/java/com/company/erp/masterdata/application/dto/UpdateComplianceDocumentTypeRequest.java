package com.company.erp.masterdata.application.dto;

import com.company.erp.masterdata.domain.ComplianceOwnerType;
import com.company.erp.masterdata.domain.ProductType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.Set;

public record UpdateComplianceDocumentTypeRequest(
        @NotBlank @Size(max = 200) String name,
        @NotNull ComplianceOwnerType appliesTo,
        boolean hasExpiry,
        Set<ProductType> requiredProductTypes,
        @NotNull Integer version) {
}
