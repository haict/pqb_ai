package com.company.erp.system.application.dto;

/** Trạng thái cấu hình số chứng từ so với hôm nay. */
public enum DocumentNumberFormatStatus {
    /** Đang áp dụng. */
    CURRENT,
    /** Sẽ áp dụng từ {@code effectiveFrom}. */
    SCHEDULED,
    /** Đã được cấu hình mới hơn thay thế; vẫn áp dụng cho chứng từ có ngày thuộc giai đoạn cũ. */
    SUPERSEDED
}
