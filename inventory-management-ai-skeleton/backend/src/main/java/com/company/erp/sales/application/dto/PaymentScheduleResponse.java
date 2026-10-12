package com.company.erp.sales.application.dto;

import com.company.erp.sales.domain.PaymentDueBasis;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Đợt thu tiền của đơn (BR-SAL-50): số phải thu theo tỷ lệ, hạn (trống khi mốc chưa xảy ra), số đã thu phân bổ theo
 * thứ tự đợt từ tiền đã thu của các hóa đơn thuộc đơn.
 */
public record PaymentScheduleResponse(
        int installmentNo,
        BigDecimal percent,
        BigDecimal amount,
        PaymentDueBasis dueBasis,
        int dueDays,
        LocalDate dueDate,
        BigDecimal paidAmount,
        BigDecimal remainingAmount,
        boolean overdue,
        String description) {
}
