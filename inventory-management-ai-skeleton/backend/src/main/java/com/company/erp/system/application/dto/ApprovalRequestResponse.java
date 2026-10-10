package com.company.erp.system.application.dto;

import com.company.erp.system.domain.ApprovalActionType;
import com.company.erp.system.domain.ApprovalRequestStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Yêu cầu duyệt kèm các bước và lịch sử xử lý.
 *
 * @param currentStepPosition thứ tự (từ 1) của bước đang chờ; bằng {@code totalSteps} khi đã kết thúc ở bước cuối
 */
public record ApprovalRequestResponse(
        Long id,
        Long companyId,
        Long branchId,
        String documentType,
        Long documentId,
        String documentNo,
        BigDecimal amount,
        ApprovalRequestStatus status,
        int currentStepPosition,
        int totalSteps,
        Long requestedBy,
        String requestedByName,
        Instant requestedAt,
        Instant completedAt,
        List<ApprovalStepResponse> steps,
        List<Action> actions) {

    public record Action(
            ApprovalActionType action,
            Integer stepNo,
            String actorName,
            String onBehalfOfName,
            String comment,
            Instant actedAt) {
    }
}
