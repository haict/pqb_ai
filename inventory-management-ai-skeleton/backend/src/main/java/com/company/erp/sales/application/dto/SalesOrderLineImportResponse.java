package com.company.erp.sales.application.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Dòng hàng đọc từ file Excel để điền vào form lập / sửa đơn bán; chưa lưu gì. {@code unitPrice} trống: server lấy giá
 * theo bảng giá khi lưu (BR-SAL-02); {@code discountPercent} trống: chiết khấu đại lý của khách; {@code lotNo} trống:
 * kho chọn theo FEFO khi ghi sổ. Cột thuế suất trống thì {@code taxRateId} là thuế suất mặc định của hàng.
 */
public record SalesOrderLineImportResponse(int created, List<Line> lines) {

    public record Line(Long productId, String productCode, String productName, Long unitId, BigDecimal quantity,
                       BigDecimal unitPrice, BigDecimal discountPercent, Long taxRateId, boolean promotional,
                       LocalDate expectedDate, String lotNo, String lotOverrideReason, List<String> serialNumbers,
                       String description) {
    }
}
