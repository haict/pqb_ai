package com.company.erp.sales.application.dto;

import com.company.erp.sales.domain.ComplaintDecision;
import com.company.erp.sales.domain.ComplaintStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/** Phiếu tiếp nhận hàng lỗi. */
public record SalesComplaintResponse(
        Long id,
        Long companyId,
        Long branchId,
        String complaintNo,
        LocalDate receivedDate,
        Long customerId,
        String customerCode,
        String customerName,
        Long deliveryId,
        Long salesInvoiceId,
        Long productId,
        String productCode,
        String productName,
        String lotNo,
        String serialNo,
        BigDecimal quantity,
        String description,
        String inspectionResult,
        ComplaintDecision decision,
        String resolutionNote,
        Long salesReturnId,
        ComplaintStatus status,
        Instant inspectedAt,
        Instant resolvedAt,
        Long createdBy,
        int version) {
}
