package com.company.erp.finance.application.dto;

import com.company.erp.finance.domain.DebtType;
import com.company.erp.finance.domain.FinanceDocumentStatus;
import com.company.erp.system.application.dto.ApprovalRequestResponse;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** Điều chỉnh công nợ / thanh toán tạm ứng; {@code lines}, {@code approvals} chỉ có ở API chi tiết. */
public record DebtAdjustmentResponse(
        Long id,
        Long companyId,
        Long branchId,
        String documentNo,
        String documentType,
        DebtType debtType,
        Long partnerId,
        String partnerCode,
        String partnerName,
        Long reconciliationId,
        LocalDate documentDate,
        LocalDate postingDate,
        String description,
        BigDecimal totalAmount,
        FinanceDocumentStatus status,
        Instant submittedAt,
        Instant approvedAt,
        Instant postedAt,
        Instant reversedAt,
        LocalDate reverseDate,
        String reason,
        Long createdBy,
        int version,
        List<DebtAdjustmentLineResponse> lines,
        List<ApprovalRequestResponse> approvals) {
}
