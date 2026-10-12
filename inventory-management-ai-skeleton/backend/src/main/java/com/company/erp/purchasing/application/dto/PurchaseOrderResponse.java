package com.company.erp.purchasing.application.dto;

import com.company.erp.purchasing.domain.PurchaseDocumentStatus;
import com.company.erp.system.application.dto.ApprovalRequestResponse;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** Đơn mua hàng; {@code lines}, {@code approvals} chỉ có ở API chi tiết. */
public record PurchaseOrderResponse(
        Long id,
        Long companyId,
        Long branchId,
        String documentNo,
        LocalDate documentDate,
        Long supplierId,
        String supplierCode,
        String supplierName,
        Long warehouseId,
        String warehouseCode,
        String warehouseName,
        LocalDate expectedDate,
        Integer paymentTermDays,
        String currencyCode,
        BigDecimal exchangeRate,
        String description,
        BigDecimal amount,
        BigDecimal discountAmount,
        BigDecimal taxAmount,
        BigDecimal totalAmount,
        PurchaseDocumentStatus status,
        Instant submittedAt,
        Instant approvedAt,
        Instant closedAt,
        String reason,
        Long createdBy,
        int version,
        List<PurchaseOrderLineResponse> lines,
        List<ApprovalRequestResponse> approvals) {
}
