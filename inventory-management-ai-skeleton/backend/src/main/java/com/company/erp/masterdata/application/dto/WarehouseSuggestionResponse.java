package com.company.erp.masterdata.application.dto;

/** Kho gợi ý cho hàng theo tên nhóm hàng (D-76); {@code warehouseId} trống khi không tìm được kho duy nhất. */
public record WarehouseSuggestionResponse(Long productId, Long warehouseId) {
}
