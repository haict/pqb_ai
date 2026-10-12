package com.company.erp.purchasing.application.dto;

import com.company.erp.purchasing.domain.PurchaseDocumentStatus;
import com.company.erp.system.application.dto.ApprovalRequestResponse;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * Hóa đơn mua; {@code matchWarnings}: cảnh báo đối chiếu ba chiều đơn mua - phiếu nhập - hóa đơn (BR-PUR-11).
 * {@code lines}, {@code approvals}, {@code matchWarnings} chỉ có ở API chi tiết.
 */
public record PurchaseInvoiceResponse(
        Long id,
        Long companyId,
        Long branchId,
        String documentNo,
        LocalDate documentDate,
        LocalDate postingDate,
        Long supplierId,
        String supplierCode,
        String supplierName,
        String sellerTaxCode,
        String invoiceSeries,
        String invoiceNo,
        LocalDate invoiceDate,
        LocalDate dueDate,
        String description,
        BigDecimal amount,
        BigDecimal taxAmount,
        BigDecimal totalAmount,
        PurchaseDocumentStatus status,
        Instant submittedAt,
        Instant approvedAt,
        Instant postedAt,
        Instant reversedAt,
        LocalDate reverseDate,
        String reason,
        Long createdBy,
        int version,
        List<String> matchWarnings,
        List<PurchaseInvoiceLineResponse> lines,
        List<ApprovalRequestResponse> approvals) {
}
