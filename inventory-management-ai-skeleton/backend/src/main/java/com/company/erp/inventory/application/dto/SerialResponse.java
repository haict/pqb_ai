package com.company.erp.inventory.application.dto;

import com.company.erp.inventory.domain.SerialStatus;

public record SerialResponse(
        Long id,
        Long productId,
        String productCode,
        String productName,
        String serialNo,
        Long lotId,
        SerialStatus lifecycleStatus,
        Long currentWarehouseId,
        boolean blocked,
        String blockReason,
        int version) {
}
