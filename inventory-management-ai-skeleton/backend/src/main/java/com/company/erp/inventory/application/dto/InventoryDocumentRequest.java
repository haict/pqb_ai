package com.company.erp.inventory.application.dto;

import com.company.erp.inventory.domain.InventoryDocumentType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;

/**
 * Tạo / sửa chứng từ kho. Chi nhánh lấy theo kho; số chứng từ, trạng thái, tổng tiền do server quyết định
 * (api.md mục 3). {@code postingDate} trống thì bằng ngày chứng từ. {@code version} bắt buộc khi sửa.
 * Kho (D-77): phiếu nhập, xuất, xuất hủy, điều chỉnh, chuyển trạng thái chọn kho theo dòng
 * ({@code lines[].warehouseId}); {@code warehouseId} đầu phiếu không bắt buộc, là kho cho dòng trống kho; kho đầu
 * phiếu lưu = kho dòng đầu tiên. Phiếu chuyển kho bắt buộc {@code warehouseId} (kho xuất) và kho đích.
 */
public record InventoryDocumentRequest(
        @NotNull InventoryDocumentType documentType,
        @Size(max = 30) String subType,
        @NotNull LocalDate documentDate,
        LocalDate postingDate,
        Long warehouseId,
        Long destinationWarehouseId,
        Long partnerId,
        Long reasonId,
        @Size(max = 500) String reasonText,
        @Size(max = 1000) String note,
        Long recallId,
        Long issuePolicyId,
        @NotNull @Size(max = 500) List<@Valid @NotNull InventoryLineRequest> lines,
        Integer version) {
}
