package com.company.erp.purchasing.application.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Dòng đề nghị mua đã duyệt còn số chưa đặt (đơn vị cơ sở), để lập đơn mua theo đề nghị. */
public record OpenRequisitionLineResponse(
        Long requisitionId,
        String documentNo,
        LocalDate documentDate,
        Long lineId,
        int lineNo,
        Long productId,
        String productCode,
        String productName,
        Long baseUnitId,
        BigDecimal remainingQuantity,
        Long suggestedSupplierId,
        LocalDate requiredDate) {
}
