package com.company.erp.masterdata.application.dto;

import com.company.erp.common.domain.RecordStatus;
import com.company.erp.masterdata.domain.ComplianceOwnerType;
import java.time.LocalDate;

/** Hồ sơ, giấy phép; {@code daysToExpiry} âm khi đã hết hạn, {@code null} khi không có hạn. */
public record ComplianceDocumentResponse(
        Long id,
        Long companyId,
        Long documentTypeId,
        ComplianceOwnerType ownerType,
        Long ownerId,
        String documentNo,
        LocalDate issuedDate,
        LocalDate expiryDate,
        Long daysToExpiry,
        String issuer,
        String note,
        RecordStatus status,
        int version) {
}
