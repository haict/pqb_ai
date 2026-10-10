package com.company.erp.system.domain;

/** Chế độ duyệt của loại chứng từ (BR-GEN-05, D-04). */
public enum ApprovalMode {
    /** Không duyệt: DRAFT chuyển thẳng sang ghi sổ, vẫn ghi người và thời điểm. */
    NONE,
    /** Duyệt khi có luồng duyệt khớp (chi nhánh, ngưỡng); không khớp thì không cần duyệt. */
    CONFIGURED,
    /** Bắt buộc duyệt (D-04); không có luồng khớp thì không gửi duyệt được. */
    REQUIRED
}
