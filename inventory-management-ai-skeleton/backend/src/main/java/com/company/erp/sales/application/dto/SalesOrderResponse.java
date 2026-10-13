package com.company.erp.sales.application.dto;

import com.company.erp.sales.domain.DeliveryStatus;
import com.company.erp.sales.domain.SalesDocumentStatus;
import com.company.erp.system.application.dto.ApprovalRequestResponse;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * Đơn bán (D-54): hóa đơn điện tử, phiếu xuất kho sinh khi ghi sổ, theo dõi giao hàng. {@code lines} (kèm hàng thực
 * xuất, tồn khả dụng), {@code paymentSchedule}, {@code availabilityWarnings}, {@code approvals} chỉ có ở API chi tiết.
 */
public record SalesOrderResponse(
        Long id,
        Long companyId,
        Long branchId,
        String documentNo,
        LocalDate documentDate,
        LocalDate postingDate,
        Long customerId,
        String customerCode,
        String customerName,
        String customerTaxCode,
        String contactName,
        String deliveryAddress,
        String receiverName,
        String receiverPhone,
        Long warehouseId,
        String warehouseCode,
        String warehouseName,
        LocalDate expectedDate,
        Integer paymentTermDays,
        LocalDate dueDate,
        boolean reserveStock,
        Instant reservedUntil,
        Long salesUserId,
        String salesUserName,
        Long sourceReturnId,
        String currencyCode,
        String description,
        BigDecimal amount,
        BigDecimal discountAmount,
        BigDecimal taxAmount,
        BigDecimal totalAmount,
        String approvalReasons,
        String einvoiceSeries,
        String einvoiceNo,
        LocalDate einvoiceDate,
        String taxAuthorityCode,
        Long inventoryDocumentId,
        String inventoryDocumentNo,
        DeliveryStatus deliveryStatus,
        String shipperName,
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
        List<String> availabilityWarnings,
        List<SalesLineResponse> lines,
        List<PaymentScheduleResponse> paymentSchedule,
        List<ApprovalRequestResponse> approvals) {
}
