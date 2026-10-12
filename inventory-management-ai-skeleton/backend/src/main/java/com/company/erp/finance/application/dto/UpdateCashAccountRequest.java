package com.company.erp.finance.application.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Sửa tên, tài khoản kế toán, thông tin ngân hàng của quỹ; gửi version đã đọc. */
public record UpdateCashAccountRequest(
        @NotBlank @Size(max = 200) String name,
        @NotNull Long glAccountId,
        @Size(max = 200) String bankName,
        @Size(max = 50) String bankAccountNo,
        @NotNull Integer version) {
}
