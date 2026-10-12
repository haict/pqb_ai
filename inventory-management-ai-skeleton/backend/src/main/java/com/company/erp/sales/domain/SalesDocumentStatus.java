package com.company.erp.sales.domain;

/**
 * Trạng thái chứng từ bán hàng (BR mục 5.1, BR-SAL-05). Báo giá: DRAFT → SUBMITTED (chờ duyệt giá) → APPROVED → SENT →
 * ACCEPTED / DECLINED / EXPIRED; CLOSED khi được thay bằng phiên bản mới. Đơn bán (nhóm B): APPROVED → IN_PROGRESS →
 * COMPLETED / CLOSED. Giao hàng, hóa đơn, trả hàng (nhóm A): POSTED, REVERSED.
 */
public enum SalesDocumentStatus {
    DRAFT,
    SUBMITTED,
    REJECTED,
    APPROVED,
    SENT,
    ACCEPTED,
    DECLINED,
    EXPIRED,
    IN_PROGRESS,
    COMPLETED,
    CLOSED,
    CANCELLED,
    POSTED,
    REVERSED
}
