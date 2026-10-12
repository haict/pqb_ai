package com.company.erp.finance.application.dto;

import com.company.erp.finance.domain.FinanceDocumentStatus;
import com.company.erp.system.application.dto.ApprovalRequestResponse;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * Kiểm kê quỹ. {@code bookBalance}, {@code differenceAmount} chốt khi ghi sổ; trước đó {@code currentBookBalance} là
 * số dư sổ quỹ đến hết ngày kiểm kê tính tại lúc xem (chỉ có ở API chi tiết).
 */
public record CashCountResponse(
        Long id,
        Long companyId,
        Long branchId,
        String documentNo,
        Long cashAccountId,
        String cashAccountCode,
        String cashAccountName,
        LocalDate documentDate,
        LocalDate postingDate,
        String description,
        BigDecimal countedAmount,
        BigDecimal bookBalance,
        BigDecimal differenceAmount,
        BigDecimal currentBookBalance,
        FinanceDocumentStatus status,
        Instant submittedAt,
        Instant approvedAt,
        Instant postedAt,
        Instant reversedAt,
        LocalDate reverseDate,
        String reason,
        Long createdBy,
        int version,
        List<ApprovalRequestResponse> approvals) {
}
