package com.company.erp.reporting.application.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Xuất bán trong kỳ theo đơn bán đã / chưa có số hóa đơn (D-68); số lượng theo đơn vị cơ sở, trả lại đã trừ.
 */
public record IssuedInvoiceStatusResponse(LocalDate dateFrom, LocalDate dateTo, List<Row> rows) {

    public record Row(Long productId, Long warehouseId, BigDecimal invoicedQuantity, BigDecimal uninvoicedQuantity) {
    }

    /** Đơn bán cấu thành số lượng (truy ngược số liệu, business-integrity mục 11). */
    public record Order(Long orderId, String documentNo, LocalDate postingDate, String customerCode,
                        String customerName, String einvoiceSeries, String einvoiceNo, LocalDate einvoiceDate,
                        BigDecimal quantity) {
    }

    /** @param truncated còn đơn ngoài giới hạn số dòng trả về */
    public record Orders(List<Order> orders, boolean truncated) {
    }
}
