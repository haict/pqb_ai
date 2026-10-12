package com.company.erp.sales.application.dto;

import com.company.erp.sales.domain.SalesDocumentStatus;
import com.company.erp.system.application.dto.ApprovalRequestResponse;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * Hóa đơn bán; {@code warnings} (lập trước khi khách nhận hàng), {@code lines}, {@code approvals} chỉ có ở API chi
 * tiết.
 */
public record SalesInvoiceResponse(
        Long id,
        Long companyId,
        Long branchId,
        String documentNo,
        LocalDate documentDate,
        LocalDate postingDate,
        Long customerId,
        String customerCode,
        String customerName,
        String buyerName,
        String buyerTaxCode,
        String buyerAddress,
        LocalDate invoiceDate,
        LocalDate dueDate,
        String einvoiceSeries,
        String einvoiceNo,
        LocalDate einvoiceDate,
        String taxAuthorityCode,
        Long salesUserId,
        String description,
        BigDecimal amount,
        BigDecimal discountAmount,
        BigDecimal taxAmount,
        BigDecimal totalAmount,
        SalesDocumentStatus status,
        Instant submittedAt,
        Instant approvedAt,
        Instant postedAt,
        Instant reversedAt,
        LocalDate reverseDate,
        String reason,
        Long createdBy,
        int version,
        List<String> warnings,
        List<SalesInvoiceLineResponse> lines,
        List<ApprovalRequestResponse> approvals) {
}
