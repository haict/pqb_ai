package com.company.erp.inventory.application.dto;

import com.company.erp.inventory.domain.RecallStatus;
import com.company.erp.system.application.dto.ApprovalRequestResponse;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record RecallResponse(
        Long id,
        Long companyId,
        Long branchId,
        String documentNo,
        LocalDate recallDate,
        Long productId,
        String productCode,
        String productName,
        Long lotId,
        String lotNo,
        Long serialId,
        String serialNo,
        String reason,
        RecallStatus status,
        Instant approvedAt,
        Instant closedAt,
        String cancelReason,
        int version,
        List<RecallCustomerResponse> customers,
        List<ApprovalRequestResponse> approvals) {
}
