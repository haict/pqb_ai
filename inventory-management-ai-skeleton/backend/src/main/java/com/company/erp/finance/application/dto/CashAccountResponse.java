package com.company.erp.finance.application.dto;

import com.company.erp.common.domain.RecordStatus;
import com.company.erp.finance.domain.CashAccountType;
import java.math.BigDecimal;

/** Quỹ / tài khoản ngân hàng; {@code balance} là số dư hiện tại tính từ sổ quỹ. */
public record CashAccountResponse(
        Long id,
        Long companyId,
        Long branchId,
        String code,
        String name,
        CashAccountType accountType,
        Long glAccountId,
        String glAccountCode,
        String bankName,
        String bankAccountNo,
        String currencyCode,
        BigDecimal balance,
        RecordStatus status,
        int version) {
}
