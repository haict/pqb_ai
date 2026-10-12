package com.company.erp.purchasing.domain;

/**
 * Trạng thái chứng từ module mua hàng (BR mục 5.1). Nhóm B (đề nghị mua, đơn mua): sau khi duyệt chuyển IN_PROGRESS
 * khi có chứng từ đích một phần, COMPLETED khi đủ, CLOSED khi đóng phần còn lại. Nhóm A (phiếu nhận hàng): POSTED khi
 * xác nhận nhận hàng, REVERSED khi đảo.
 */
public enum PurchaseDocumentStatus {
    DRAFT,
    SUBMITTED,
    REJECTED,
    APPROVED,
    IN_PROGRESS,
    COMPLETED,
    CLOSED,
    CANCELLED,
    POSTED,
    REVERSED
}
