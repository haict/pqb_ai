package com.company.erp.finance.application.dto;

import com.company.erp.finance.domain.DebtType;
import com.company.erp.finance.domain.FinanceDocumentStatus;
import com.company.erp.finance.domain.OffsetType;
import com.company.erp.system.application.dto.ApprovalRequestResponse;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** Chứng từ bù trừ công nợ; {@code lines}, {@code approvals} chỉ có ở API chi tiết. */
public record DebtOffsetResponse(
        Long id,
        Long companyId,
        Long branchId,
        String documentNo,
        OffsetType offsetType,
        DebtType debtType,
        Long partnerId,
        String partnerCode,
        String partnerName,
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
        List<DebtOffsetLineResponse> lines,
        List<ApprovalRequestResponse> approvals) {
}
