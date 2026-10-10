package com.company.erp.inventory.application.dto;

import com.company.erp.inventory.domain.PolicyStatus;
import com.company.erp.system.application.dto.ApprovalRequestResponse;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record StockIssuePolicyResponse(
        Long id,
        Long companyId,
        Long branchId,
        String documentNo,
        Long warehouseId,
        Long productId,
        BigDecimal maxQuantity,
        BigDecimal usedQuantity,
        Long usedByDocumentId,
        LocalDate validFrom,
        LocalDate validTo,
        String reason,
        PolicyStatus status,
        Instant approvedAt,
        String cancelReason,
        int version,
        List<ApprovalRequestResponse> approvals) {
}
