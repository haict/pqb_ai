package com.company.erp.finance.domain;

/**
 * Loại chứng từ thu chi (BR-CSH-02), cũng là mã loại chứng từ trong {@code sys_document_types}: chiều tiền và loại quỹ
 * dùng được ({@code null} = mọi loại).
 */
public enum CashDocumentType {
    /** Phiếu thu tiền mặt. */
    PT(CashDirection.IN, CashAccountType.CASH),
    /** Phiếu chi tiền mặt. */
    PC(CashDirection.OUT, CashAccountType.CASH),
    /** Giấy báo có. */
    BC(CashDirection.IN, CashAccountType.BANK),
    /** Ủy nhiệm chi. */
    UNC(CashDirection.OUT, CashAccountType.BANK),
    /** Chuyển tiền nội bộ giữa các quỹ, tài khoản. */
    CTN(CashDirection.TRANSFER, null);

    private final CashDirection direction;
    private final CashAccountType accountType;

    CashDocumentType(CashDirection direction, CashAccountType accountType) {
        this.direction = direction;
        this.accountType = accountType;
    }

    public CashDirection direction() {
        return direction;
    }

    /** Loại quỹ bắt buộc của quỹ chính; {@code null} = mọi loại. */
    public CashAccountType accountType() {
        return accountType;
    }

    /** Tiền rời quỹ: từ ngưỡng chi trở lên chỉ người có quyền ghi sổ vượt ngưỡng được ghi sổ (BR-CSH-03, D-61). */
    public boolean outflow() {
        return direction != CashDirection.IN;
    }
}
