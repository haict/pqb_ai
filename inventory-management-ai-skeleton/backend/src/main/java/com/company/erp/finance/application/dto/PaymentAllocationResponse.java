package com.company.erp.finance.application.dto;

import com.company.erp.finance.domain.PaymentAllocationStatus;
import java.math.BigDecimal;
import java.time.LocalDate;

/** Phân bổ đã ghi sổ: hóa đơn nhận tiền và số tiền (truy vết chứng từ thanh toán ↔ hóa đơn). */
public record PaymentAllocationResponse(
        Long id,
        Long debtItemId,
        String invoiceNo,
        String debtSourceNo,
        LocalDate dueDate,
        BigDecimal amount,
        PaymentAllocationStatus status) {
}
