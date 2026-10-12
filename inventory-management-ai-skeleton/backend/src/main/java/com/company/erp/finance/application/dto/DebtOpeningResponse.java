package com.company.erp.finance.application.dto;

import com.company.erp.finance.domain.DebtType;
import com.company.erp.finance.domain.FinanceDocumentStatus;
import com.company.erp.system.application.dto.ApprovalRequestResponse;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** Chứng từ công nợ đầu kỳ; {@code lines}, {@code approvals} chỉ có ở API chi tiết. */
public record DebtOpeningResponse(
        Long id,
        Long companyId,
        Long branchId,
        String documentNo,
        DebtType debtType,
        LocalDate documentDate,
        LocalDate postingDate,
        String description,
        BigDecimal totalAmount,
        int lineCount,
        FinanceDocumentStatus status,
        Instant submittedAt,
        Instant approvedAt,
        Instant postedAt,
        Instant reversedAt,
        LocalDate reverseDate,
        String reason,
        Long createdBy,
        int version,
        List<DebtOpeningLineResponse> lines,
        List<ApprovalRequestResponse> approvals) {
}
