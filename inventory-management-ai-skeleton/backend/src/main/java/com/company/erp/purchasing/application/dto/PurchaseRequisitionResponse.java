package com.company.erp.purchasing.application.dto;

import com.company.erp.purchasing.domain.PurchaseDocumentStatus;
import com.company.erp.purchasing.domain.RequisitionSource;
import com.company.erp.system.application.dto.ApprovalRequestResponse;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** Đề nghị mua; {@code lines}, {@code approvals} chỉ có ở API chi tiết. */
public record PurchaseRequisitionResponse(
        Long id,
        Long companyId,
        Long branchId,
        String documentNo,
        RequisitionSource sourceType,
        LocalDate documentDate,
        LocalDate requiredDate,
        Long requesterId,
        String requesterName,
        String description,
        PurchaseDocumentStatus status,
        Instant submittedAt,
        Instant approvedAt,
        Instant closedAt,
        String reason,
        Long createdBy,
        int version,
        List<PurchaseRequisitionLineResponse> lines,
        List<ApprovalRequestResponse> approvals) {
}
