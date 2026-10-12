package com.company.erp.finance.domain;

/** Trạng thái chứng từ ghi sổ của module công nợ, quỹ (BR mục 5.1, nhóm A). */
public enum FinanceDocumentStatus {
    DRAFT,
    SUBMITTED,
    REJECTED,
    APPROVED,
    POSTED,
    CANCELLED,
    REVERSED
}
