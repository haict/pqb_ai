package com.company.erp.system.application.dto;

import com.company.erp.system.domain.DelegationStatus;
import java.time.Instant;

/** Ủy quyền duyệt; {@code effective}: đang có hiệu lực tại thời điểm trả về. */
public record DelegationResponse(
        Long id,
        Long companyId,
        Long delegatorUserId,
        String delegatorUsername,
        Long delegateUserId,
        String delegateUsername,
        String documentType,
        Instant validFrom,
        Instant validTo,
        DelegationStatus status,
        boolean effective,
        String reason) {
}
