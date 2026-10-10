package com.company.erp.inventory.application.dto;

import com.company.erp.common.domain.QualityStatus;
import java.math.BigDecimal;
import java.time.LocalDate;

/** Tồn chi tiết theo vị trí, lô, serial, trạng thái chất lượng. */
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
        QualityStatus qualityStatus,
        BigDecimal onHandQuantity) {
}
