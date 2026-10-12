package com.company.erp.purchasing.application.dto;

import com.company.erp.purchasing.domain.InspectionResult;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** Dòng phiếu nhận hàng; {@code unitCost}: đơn giá tạm tính theo đơn mua (một đơn vị của dòng). */
public record PurchaseReceiptLineResponse(
        Long id,
        int lineNo,
        Long orderLineId,
        Long productId,
        String productCode,
        String productName,
        Long unitId,
        BigDecimal conversionFactor,
        BigDecimal quantity,
        BigDecimal baseQuantity,
        InspectionResult inspectionResult,
        String rejectReason,
        Long locationId,
        String lotNo,
        LocalDate manufactureDate,
        LocalDate expiryDate,
        BigDecimal unitCost,
        String note,
        List<String> serialNumbers) {
}
