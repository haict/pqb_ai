package com.company.erp.system.domain;

/** Trạng thái bản quyền phần mềm (D-59). Chỉ {@link #VALID} và {@link #GRACE} được ghi dữ liệu. */
public enum LicenseState {
    /** Còn hạn. */
    VALID,
    /** Đã quá ngày hết hạn nhưng còn trong thời gian ân hạn. */
    GRACE,
    /** Quá thời gian ân hạn: chỉ đọc. */
    EXPIRED,
    /** Chưa nạp license: chỉ đọc. */
    MISSING,
    /** Chữ ký sai, khóa không được tin cậy hoặc nội dung hỏng: chỉ đọc. */
    INVALID,
    /** Đồng hồ máy chủ bị lùi hoặc mốc thời gian bị sửa: chỉ đọc. */
    CLOCK_TAMPERED,
    /** Có công ty đang hoạt động mà mã số thuế không thuộc license: chỉ đọc. */
    COMPANY_NOT_LICENSED;

    public boolean writable() {
        return this == VALID || this == GRACE;
    }
}
