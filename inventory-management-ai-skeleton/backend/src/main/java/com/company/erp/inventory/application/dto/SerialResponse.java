package com.company.erp.inventory.application.dto;

import com.company.erp.inventory.domain.ItemCondition;
import com.company.erp.inventory.domain.SerialStatus;

public record SerialResponse(
        Long id,
        Long productId,
        String productCode,
        String productName,
        String serialNo,
        Long lotId,
        ItemCondition itemCondition,
        SerialStatus lifecycleStatus,
        Long currentWarehouseId,
        boolean blocked,
        String blockReason,
        int version) {
}
