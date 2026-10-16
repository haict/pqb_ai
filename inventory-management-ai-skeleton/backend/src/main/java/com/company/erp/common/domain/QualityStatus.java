package com.company.erp.common.domain;

/**
 * Trạng thái chất lượng của tồn kho (BR-INV-03). Nhập kho mặc định ĐẠT (D-56); kho theo dõi tồn theo trạng thái.
 */
public enum QualityStatus {
    /** Chờ kiểm tra. */
    PENDING_INSPECTION,
    /** Đạt: duy nhất trạng thái được xuất bán (BR-INV-23). */
    PASSED,
    /** Cách ly. */
    QUARANTINE,
    /** Không đạt. */
    FAILED,
    /** Hết hạn. */
    EXPIRED,
    /** Thu hồi. */
    RECALLED
}
