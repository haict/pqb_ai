package com.company.erp.sales.application.dto;

import java.math.BigDecimal;

/**
 * Giá đề xuất cho dòng bán (BR-SAL-02): đơn giá (trống nếu chưa có bảng giá), nguồn (CUSTOMER / CUSTOMER_GROUP /
 * LIST), mã bảng giá, giá sàn, chiết khấu mặc định (chiết khấu đại lý), thuế suất mặc định của hàng.
 */
public record PriceSuggestionResponse(
        BigDecimal unitPrice,
        String priceSource,
        String priceListCode,
        BigDecimal floorPrice,
        BigDecimal defaultDiscountPercent,
        Long taxRateId) {
}
