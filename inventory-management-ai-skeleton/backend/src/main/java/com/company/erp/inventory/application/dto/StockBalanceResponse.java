package com.company.erp.inventory.application.dto;

import com.company.erp.common.domain.QualityStatus;
import com.company.erp.inventory.domain.ItemCondition;
import java.math.BigDecimal;
import java.time.LocalDate;

/** Tồn chi tiết theo vị trí, lô, serial, trạng thái chất lượng; máy mới / cũ theo serial hoặc lô (BR-INV-43). */
public record StockBalanceResponse(
        Long id,
        Long productId,
        String productCode,
        String productName,
        Long warehouseId,
        Long locationId,
        Long lotId,
        String lotNo,
        LocalDate expiryDate,
        boolean lotBlocked,
        Long serialId,
        String serialNo,
        ItemCondition itemCondition,
        QualityStatus qualityStatus,
        BigDecimal onHandQuantity) {
}
