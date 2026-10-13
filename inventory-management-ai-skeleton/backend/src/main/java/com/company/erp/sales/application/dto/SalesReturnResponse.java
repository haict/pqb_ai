package com.company.erp.sales.application.dto;

import com.company.erp.sales.domain.SalesDocumentStatus;
import com.company.erp.sales.domain.SalesReturnType;
import com.company.erp.system.application.dto.ApprovalRequestResponse;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** Trả hàng / giảm giá hàng bán; {@code lines}, {@code approvals} chỉ có ở API chi tiết. */
public record SalesReturnResponse(
        Long id,
        Long companyId,
        Long branchId,
        String documentNo,
        SalesReturnType documentType,
        LocalDate documentDate,
        LocalDate postingDate,
        Long customerId,
        String customerCode,
        String customerName,
        Long salesOrderId,
        String salesOrderNo,
        String invoiceLabel,
        Long warehouseId,
        Long inventoryDocumentId,
        String inventoryDocumentNo,
        String description,
        BigDecimal amount,
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
        List<SalesReturnLineResponse> lines,
        List<ApprovalRequestResponse> approvals) {
}
