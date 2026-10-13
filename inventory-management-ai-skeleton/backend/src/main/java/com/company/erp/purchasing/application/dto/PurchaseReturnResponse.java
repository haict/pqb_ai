package com.company.erp.purchasing.application.dto;

import com.company.erp.purchasing.domain.PurchaseDocumentStatus;
import com.company.erp.purchasing.domain.PurchaseReturnType;
import com.company.erp.system.application.dto.ApprovalRequestResponse;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** Trả hàng / giảm giá hàng mua; {@code lines}, {@code approvals} chỉ có ở API chi tiết. */
public record PurchaseReturnResponse(
        Long id,
        Long companyId,
        Long branchId,
        String documentNo,
        PurchaseReturnType documentType,
        LocalDate documentDate,
        LocalDate postingDate,
        Long supplierId,
        String supplierCode,
        String supplierName,
        Long purchaseOrderId,
        String purchaseOrderNo,
        String invoiceLabel,
        Long warehouseId,
        Long inventoryDocumentId,
        String inventoryDocumentNo,
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
        List<PurchaseReturnLineResponse> lines,
        List<ApprovalRequestResponse> approvals) {
}
