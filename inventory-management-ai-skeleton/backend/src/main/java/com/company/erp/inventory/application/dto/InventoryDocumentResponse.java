package com.company.erp.inventory.application.dto;

import com.company.erp.inventory.domain.InventoryDocumentStatus;
import com.company.erp.inventory.domain.InventoryDocumentType;
import com.company.erp.system.application.dto.ApprovalRequestResponse;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

/**
 * Chi tiết chứng từ kho: dòng, sổ nhật ký đã ghi, lịch sử duyệt, liên kết chứng từ đảo (BR-GEN-09).
 * {@code allowedActions}: hành động người dùng có thể thực hiện, chỉ để ẩn/hiện nút; server vẫn kiểm tra.
 */
public record InventoryDocumentResponse(
        Long id,
        Long companyId,
        Long branchId,
        InventoryDocumentType documentType,
        String subType,
        String documentNo,
        LocalDate documentDate,
        LocalDate postingDate,
        InventoryDocumentStatus status,
        Long warehouseId,
        Long destinationWarehouseId,
        Long transitWarehouseId,
        Long partnerId,
        Long reasonId,
        String reasonText,
        String note,
        Long recallId,
        Long stockCountId,
        Long issuePolicyId,
        Long reversalOfId,
        String reversalOfNo,
        Long reversedById,
        String reversedByNo,
        BigDecimal totalAmount,
        Long createdBy,
        Instant createdAt,
        Instant submittedAt,
        Instant approvedAt,
        Instant postedAt,
        Instant receivedAt,
        LocalDate receivePostingDate,
        Instant cancelledAt,
        String cancelReason,
        int version,
        List<InventoryLineResponse> lines,
        List<InventoryTransactionResponse> transactions,
        List<ApprovalRequestResponse> approvals,
        Set<String> allowedActions) {
}
