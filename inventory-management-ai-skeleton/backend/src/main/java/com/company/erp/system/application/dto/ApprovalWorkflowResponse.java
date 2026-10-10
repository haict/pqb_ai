package com.company.erp.system.application.dto;

import com.company.erp.common.domain.RecordStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record ApprovalWorkflowResponse(
        Long id,
        Long companyId,
        String documentType,
        String name,
        Long branchId,
        BigDecimal minAmount,
        BigDecimal minDiscountPercent,
        int priority,
        RecordStatus status,
        List<ApprovalStepResponse> steps,
        int version,
        Instant updatedAt) {
}
