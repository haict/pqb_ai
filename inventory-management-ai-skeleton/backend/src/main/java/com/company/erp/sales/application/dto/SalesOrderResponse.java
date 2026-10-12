package com.company.erp.sales.application.dto;

import com.company.erp.sales.domain.SalesDocumentStatus;
import com.company.erp.system.application.dto.ApprovalRequestResponse;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * Đơn bán; {@code lines} (kèm tiến độ, tồn khả dụng), {@code paymentSchedule}, {@code availabilityWarnings},
 * {@code reservations}, {@code approvals} chỉ có ở API chi tiết.
 */
public record SalesOrderResponse(
        Long id,
        Long companyId,
        Long branchId,
        String documentNo,
        LocalDate documentDate,
        Long customerId,
        String customerCode,
        String customerName,
        Long quoteId,
        String quoteNo,
        String contactName,
        String deliveryAddress,
        String receiverName,
        String receiverPhone,
        Long warehouseId,
        String warehouseCode,
        String warehouseName,
        LocalDate expectedDate,
        Integer paymentTermDays,
        boolean reserveStock,
        Instant reservedUntil,
        Long salesUserId,
        String salesUserName,
        String currencyCode,
        String description,
        BigDecimal amount,
        BigDecimal discountAmount,
        BigDecimal taxAmount,
        BigDecimal totalAmount,
        String approvalReasons,
        SalesDocumentStatus status,
        Instant submittedAt,
        Instant approvedAt,
        Instant closedAt,
        String reason,
        Long createdBy,
        int version,
        List<String> availabilityWarnings,
        List<SalesLineResponse> lines,
        List<PaymentScheduleResponse> paymentSchedule,
        List<ApprovalRequestResponse> approvals) {
}
