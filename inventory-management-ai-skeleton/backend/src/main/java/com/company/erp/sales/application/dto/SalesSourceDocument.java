package com.company.erp.sales.application.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Chứng từ cấu thành số liệu báo cáo (truy ngược, business-integrity mục 11): loại (HDB / TLB / GGB), số, ngày,
 * khách, giá trị trước thuế (giảm trừ mang dấu âm).
 */
public record SalesSourceDocument(
        String documentType,
        Long documentId,
        String documentNo,
        LocalDate postingDate,
        Long customerId,
        BigDecimal amount) {
}
