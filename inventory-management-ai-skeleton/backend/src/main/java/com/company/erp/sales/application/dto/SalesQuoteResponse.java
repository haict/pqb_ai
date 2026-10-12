package com.company.erp.sales.application.dto;

import com.company.erp.sales.domain.SalesDocumentStatus;
import com.company.erp.system.application.dto.ApprovalRequestResponse;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** Báo giá; {@code lines}, {@code approvals} chỉ có ở API chi tiết. */
public record SalesQuoteResponse(
        Long id,
        Long companyId,
        Long branchId,
        String documentNo,
        LocalDate documentDate,
        int revisionNo,
        Long previousQuoteId,
        Long customerId,
        String customerCode,
        String customerName,
        String contactName,
        LocalDate validUntil,
        String deliveryTerms,
        String warrantyTerms,
        String paymentTerms,
        Long salesUserId,
        String salesUserName,
        String description,
        BigDecimal amount,
        BigDecimal discountAmount,
        BigDecimal taxAmount,
        BigDecimal totalAmount,
        String approvalReasons,
        Long salesOrderId,
        SalesDocumentStatus status,
        Instant submittedAt,
        Instant approvedAt,
        Instant sentAt,
        Instant respondedAt,
        Instant closedAt,
        String reason,
        Long createdBy,
        int version,
        List<SalesLineResponse> lines,
        List<ApprovalRequestResponse> approvals) {
}
