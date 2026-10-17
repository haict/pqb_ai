package com.company.erp.purchasing.application.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Dòng hàng đọc từ file Excel để điền vào form lập / sửa đơn mua; chưa lưu gì. Tiền, thuế vẫn do server tính khi lưu
 * đơn. Cột thuế suất trống thì {@code taxRateId} là thuế suất mặc định của hàng (có thể trống).
 */
public record PurchaseOrderLineImportResponse(int created, List<Line> lines) {

    public record Line(Long productId, String productCode, String productName, boolean serviceLine, Long unitId,
                       BigDecimal quantity, BigDecimal unitPrice, BigDecimal discountPercent, Long taxRateId,
                       String lotNo, LocalDate manufactureDate, LocalDate expiryDate, List<String> serialNumbers,
                       String note) {
    }
}
