package com.company.erp.finance.domain;

/**
 * Nội dung chứng từ thu chi, cũng là mã nghiệp vụ tra quy tắc hạch toán (ADR-11): chiều tiền, loại công nợ bị giảm
 * ({@code debtType}) hoặc được tạo ({@code createdDebt}).
 */
public enum CashPurpose {
    /** Thu tiền khách hàng: giảm phải thu. */
    CUSTOMER_RECEIPT(CashDirection.IN, DebtType.RECEIVABLE, null),
    /** Trả tiền nhà cung cấp: giảm phải trả. */
    SUPPLIER_PAYMENT(CashDirection.OUT, DebtType.PAYABLE, null),
    /** Thu khác: tài khoản đối ứng theo dòng. */
    OTHER_RECEIPT(CashDirection.IN, null, null),
    /** Chi khác: tài khoản đối ứng theo dòng. */
    OTHER_PAYMENT(CashDirection.OUT, null, null),
    INTERNAL_TRANSFER(CashDirection.TRANSFER, null, null),
    /** Chi tạm ứng cho nhân viên: tạo khoản công nợ tạm ứng (BR-CSH-04). */
    EMPLOYEE_ADVANCE(CashDirection.OUT, null, DebtType.ADVANCE),
    /** Nhân viên hoàn ứng bằng tiền: giảm công nợ tạm ứng. */
    ADVANCE_RETURN(CashDirection.IN, DebtType.ADVANCE, null);

    private final CashDirection direction;
    private final DebtType debtType;
    private final DebtType createdDebt;

    CashPurpose(CashDirection direction, DebtType debtType, DebtType createdDebt) {
        this.direction = direction;
        this.debtType = debtType;
        this.createdDebt = createdDebt;
    }

    public CashDirection direction() {
        return direction;
    }

    /** Loại công nợ được thanh toán (giảm); {@code null} khi không giảm công nợ. */
    public DebtType debtType() {
        return debtType;
    }

    /** Loại công nợ được tạo; {@code null} khi không tạo công nợ. */
    public DebtType createdDebt() {
        return createdDebt;
    }

    public boolean settlesDebt() {
        return debtType != null;
    }

    /** Loại công nợ của đối tượng chứng từ (giảm hoặc tạo); {@code null} khi đối tượng không bắt buộc. */
    public DebtType partnerDebtType() {
        return debtType != null ? debtType : createdDebt;
    }

    /** Dòng sổ chi tiết công nợ khi phân bổ: thu tiền hoặc chi tiền. */
    public DebtEntryType debtEntryType() {
        return direction == CashDirection.IN ? DebtEntryType.RECEIPT : DebtEntryType.PAYMENT;
    }
}
