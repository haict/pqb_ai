package com.company.erp.purchasing.application.dto;

import com.company.erp.purchasing.domain.PurchaseDocumentStatus;
import com.company.erp.system.application.dto.ApprovalRequestResponse;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * Phiếu nhận hàng; {@code inventoryDocumentId/No}: phiếu nhập kho đã lập (truy vết hai chiều). {@code lines},
 * {@code approvals} chỉ có ở API chi tiết.
 */
public record PurchaseReceiptResponse(
        Long id,
        Long companyId,
        Long branchId,
        String documentNo,
        LocalDate documentDate,
        LocalDate postingDate,
        Long purchaseOrderId,
        String purchaseOrderNo,
        Long supplierId,
        String supplierCode,
        String supplierName,
        Long warehouseId,
        String warehouseCode,
        String warehouseName,
        String deliveryNoteNo,
        String description,
        boolean overTolerance,
        PurchaseDocumentStatus status,
        Long inventoryDocumentId,
        String inventoryDocumentNo,
        Instant stockedAt,
        Instant submittedAt,
        Instant approvedAt,
        Instant postedAt,
        Instant reversedAt,
        LocalDate reverseDate,
        String reason,
        Long createdBy,
        int version,
        List<PurchaseReceiptLineResponse> lines,
        List<ApprovalRequestResponse> approvals) {
}
