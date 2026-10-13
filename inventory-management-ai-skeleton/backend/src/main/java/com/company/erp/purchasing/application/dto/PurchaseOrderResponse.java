package com.company.erp.purchasing.application.dto;

import com.company.erp.purchasing.domain.PurchaseDocumentStatus;
import com.company.erp.system.application.dto.ApprovalRequestResponse;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * Đơn mua hàng (D-54); {@code inventoryDocumentNo}: phiếu nhập kho sinh khi ghi sổ. {@code supplierTaxCode},
 * {@code supplierAddress}: MST, địa chỉ hiện tại của nhà cung cấp trong danh mục (chỉ đọc). {@code lines}, {@code approvals}
 * chỉ có ở API chi tiết.
 */
public record PurchaseOrderResponse(
        Long id,
        Long companyId,
        Long branchId,
        String documentNo,
        LocalDate documentDate,
        LocalDate postingDate,
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
        String supplierTaxCode,
        String supplierAddress,
        String invoiceSeries,
        String invoiceNo,
        LocalDate invoiceDate,
        LocalDate dueDate,
        String description,
        BigDecimal amount,
        BigDecimal discountAmount,
        BigDecimal taxAmount,
        BigDecimal totalAmount,
        PurchaseDocumentStatus status,
        Long inventoryDocumentId,
        String inventoryDocumentNo,
        Instant submittedAt,
        Instant approvedAt,
        Instant postedAt,
        Instant reversedAt,
        LocalDate reverseDate,
        String reason,
        Long createdBy,
        int version,
        List<PurchaseOrderLineResponse> lines,
        List<ApprovalRequestResponse> approvals) {
}
