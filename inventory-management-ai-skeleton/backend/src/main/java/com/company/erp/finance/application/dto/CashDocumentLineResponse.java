package com.company.erp.finance.application.dto;

import java.math.BigDecimal;

/** Dòng chứng từ thu chi: hóa đơn được phân bổ hoặc tài khoản đối ứng. */
public record CashDocumentLineResponse(
        Long id,
        int lineNo,
        Long debtItemId,
        String invoiceNo,
        BigDecimal debtRemainingAmount,
        Long accountId,
        String accountCode,
        String accountName,
        Long costItemId,
        String costItemName,
        BigDecimal amount,
        String description) {
}
