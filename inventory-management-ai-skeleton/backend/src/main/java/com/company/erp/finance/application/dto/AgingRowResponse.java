package com.company.erp.finance.application.dto;

import java.math.BigDecimal;

/** Số còn nợ theo nhóm tuổi nợ của một đối tượng (hoặc dòng tổng khi {@code partnerId = null}). */
public record AgingRowResponse(
        Long partnerId,
        String partnerCode,
        String partnerName,
        BigDecimal notDue,
        BigDecimal bucket1,
        BigDecimal bucket2,
        BigDecimal bucket3,
        BigDecimal bucket4,
        BigDecimal total) {
}
