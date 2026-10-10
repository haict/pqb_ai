package com.company.erp.inventory.application.dto;

import com.company.erp.inventory.domain.StockCountStatus;
import com.company.erp.system.application.dto.ApprovalRequestResponse;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record StockCountResponse(
        Long id,
        Long companyId,
        Long branchId,
        Long warehouseId,
        String documentNo,
        LocalDate countDate,
        StockCountStatus status,
        String note,
        Instant startedAt,
        Instant postedAt,
        Long adjustmentDocumentId,
        String cancelReason,
        int version,
        List<StockCountLineResponse> lines,
        List<ApprovalRequestResponse> approvals) {
}
