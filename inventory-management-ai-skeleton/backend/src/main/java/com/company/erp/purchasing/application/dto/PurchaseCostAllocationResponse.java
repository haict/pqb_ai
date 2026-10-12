package com.company.erp.purchasing.application.dto;

import com.company.erp.purchasing.domain.CostAllocationMethod;
import com.company.erp.purchasing.domain.PurchaseDocumentStatus;
import com.company.erp.system.application.dto.ApprovalRequestResponse;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** Phân bổ chi phí mua hàng; {@code sources}, {@code targets}, {@code approvals} chỉ có ở API chi tiết. */
public record PurchaseCostAllocationResponse(
        Long id,
        Long companyId,
        Long branchId,
        String documentNo,
        LocalDate documentDate,
        LocalDate postingDate,
        CostAllocationMethod method,
        String description,
        BigDecimal amount,
        PurchaseDocumentStatus status,
        Instant submittedAt,
        Instant approvedAt,
        Instant postedAt,
        Instant reversedAt,
        LocalDate reverseDate,
        String reason,
        Long createdBy,
        int version,
        List<Long> receiptIds,
        List<CostAllocationSourceResponse> sources,
        List<CostAllocationTargetResponse> targets,
        List<ApprovalRequestResponse> approvals) {
}
