package com.company.erp.purchasing.domain;

/** Cơ sở phân bổ chi phí mua hàng (BR-PUR-12). */
public enum CostAllocationMethod {
    /** Theo giá trị nhập tạm tính của dòng phiếu nhận. */
    VALUE,
    /** Theo số lượng nhập (đơn vị cơ sở). */
    QUANTITY
}
