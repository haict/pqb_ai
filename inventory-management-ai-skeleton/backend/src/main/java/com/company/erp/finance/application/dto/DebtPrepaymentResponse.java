package com.company.erp.finance.application.dto;

import com.company.erp.finance.domain.DebtType;
import java.math.BigDecimal;

/** Tiền trả trước còn chưa bù trừ của đối tượng trong một chi nhánh. */
public record DebtPrepaymentResponse(Long branchId, DebtType debtType, Long partnerId, BigDecimal amount) {
}
