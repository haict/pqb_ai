package com.company.erp.purchasing.application.dto;

import com.company.erp.common.domain.QualityStatus;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Dòng còn trả / giảm giá được: theo dòng phiếu nhận chưa có hóa đơn ({@code invoiceLineId} trống) hoặc theo dòng
 * hóa đơn. Số lượng theo đơn vị của dòng nguồn; {@code remainingAmount}: giá trị còn giảm được của dòng hóa đơn.
 */
public record ReturnableLineResponse(
        Long receiptId,
        String receiptNo,
        LocalDate receiptDate,
        Long receiptLineId,
        Long warehouseId,
        Long invoiceLineId,
        Long productId,
        String productCode,
        String productName,
        Long unitId,
        String lotNo,
        LocalDate expiryDate,
        QualityStatus qualityStatus,
        BigDecimal remainingQuantity,
        BigDecimal unitPrice,
        BigDecimal taxRate,
        BigDecimal remainingAmount,
        List<String> serialNumbers) {
}
