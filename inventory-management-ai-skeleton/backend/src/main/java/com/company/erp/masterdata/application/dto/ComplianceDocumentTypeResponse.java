package com.company.erp.masterdata.application.dto;

import com.company.erp.common.domain.RecordStatus;
import com.company.erp.masterdata.domain.ComplianceOwnerType;
import com.company.erp.masterdata.domain.ProductType;
import java.util.Set;

public record ComplianceDocumentTypeResponse(
        Long id,
        Long companyId,
        String code,
        String name,
        ComplianceOwnerType appliesTo,
        boolean hasExpiry,
        Set<ProductType> requiredProductTypes,
        RecordStatus status,
        int version) {
}
