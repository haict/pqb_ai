package com.company.erp.finance.application.dto;

import com.company.erp.finance.domain.AllocationMode;
import com.company.erp.finance.domain.CashDocumentType;
import com.company.erp.finance.domain.CashPurpose;
import com.company.erp.finance.domain.FinanceDocumentStatus;
import com.company.erp.system.application.dto.ApprovalRequestResponse;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** Chứng từ thu chi; {@code lines}, {@code allocations}, {@code approvals} chỉ có ở API chi tiết. */
public record CashDocumentResponse(
        Long id,
        Long companyId,
        Long branchId,
        String documentNo,
        CashDocumentType documentType,
        CashPurpose purpose,
        LocalDate documentDate,
        LocalDate postingDate,
        Long cashAccountId,
        String cashAccountCode,
        String cashAccountName,
        Long targetCashAccountId,
        String targetCashAccountCode,
        String targetCashAccountName,
        Long partnerId,
        String partnerCode,
        String partnerName,
        String counterpartyName,
        AllocationMode allocationMode,
        String description,
        BigDecimal totalAmount,
        BigDecimal allocatedAmount,
        BigDecimal prepaymentAmount,
        FinanceDocumentStatus status,
        Instant submittedAt,
        Instant approvedAt,
        Instant postedAt,
        Instant reversedAt,
        LocalDate reverseDate,
        String reason,
        Long createdBy,
        int version,
        List<CashDocumentLineResponse> lines,
        List<PaymentAllocationResponse> allocations,
        List<ApprovalRequestResponse> approvals) {
}
