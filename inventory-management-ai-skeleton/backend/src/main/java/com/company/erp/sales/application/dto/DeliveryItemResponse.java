package com.company.erp.sales.application.dto;

import com.company.erp.common.domain.QualityStatus;
import java.math.BigDecimal;
import java.time.LocalDate;

/** Hàng thực xuất: lô, hạn dùng, serial, trạng thái, số lượng (đơn vị cơ sở). */
public record DeliveryItemResponse(
        String lotNo,
        LocalDate expiryDate,
        String serialNo,
        QualityStatus qualityStatus,
        BigDecimal baseQuantity) {
}
