package com.company.erp.sales.domain;

/**
 * Trạng thái chứng từ bán hàng (BR mục 5.1, D-54). Mọi chứng từ bán là chứng từ ghi sổ (đơn bán, trả hàng / giảm giá):
 * DRAFT → SUBMITTED → APPROVED → POSTED → REVERSED; SUBMITTED → REJECTED → sửa → gửi lại; CANCELLED khi chưa ghi sổ.
 */
public enum SalesDocumentStatus {
    DRAFT,
    SUBMITTED,
    REJECTED,
    APPROVED,
    CANCELLED,
    POSTED,
    REVERSED
}
