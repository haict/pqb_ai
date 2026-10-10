package com.company.erp.system.application.dto;

import com.company.erp.system.domain.ApprovalMode;
import java.time.Instant;

public record DocumentTypeResponse(
        String code,
        String module,
        String name,
        ApprovalMode approvalMode,
        boolean selfApprovalAllowed,
        int version,
        Instant updatedAt) {
}
