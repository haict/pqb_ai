package com.company.erp.sales.application.dto;

import com.company.erp.sales.domain.DeliveryStatus;
import com.company.erp.sales.domain.SalesDocumentStatus;
import com.company.erp.system.application.dto.ApprovalRequestResponse;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** Phiếu giao hàng; {@code lines}, {@code approvals} chỉ có ở API chi tiết. */
public record SalesDeliveryResponse(
        Long id,
        Long companyId,
        Long branchId,
        String documentNo,
        LocalDate documentDate,
        LocalDate postingDate,
        Long orderId,
        String orderNo,
        Long customerId,
        String customerCode,
        String customerName,
        Long warehouseId,
        String warehouseCode,
        String deliveryAddress,
        String receiverName,
        String receiverPhone,
        String shipperName,
        Long sourceReturnId,
        String description,
        DeliveryStatus deliveryStatus,
        Long inventoryDocumentId,
        String inventoryDocumentNo,
        Instant shippedAt,
        LocalDate receivedDate,
        String receivedBy,
        String signedDocumentRef,
        String deliveryNote,
        SalesDocumentStatus status,
        Instant submittedAt,
        Instant approvedAt,
        Instant postedAt,
        Instant reversedAt,
        LocalDate reverseDate,
        String reason,
        Long createdBy,
        int version,
        List<SalesDeliveryLineResponse> lines,
        List<ApprovalRequestResponse> approvals) {
}
