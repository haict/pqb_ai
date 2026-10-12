package com.company.erp.finance.application.dto;

import com.company.erp.finance.domain.CashAccountType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Thêm quỹ tiền mặt / tài khoản ngân hàng; mã, chi nhánh, loại không đổi sau khi tạo. */
public record CashAccountRequest(
        @NotNull Long branchId,
        @NotBlank @Pattern(regexp = "^[A-Z0-9_.-]{1,30}$", message = "Gồm 1-30 ký tự A-Z, 0-9, _ . -") String code,
        @NotBlank @Size(max = 200) String name,
        @NotNull CashAccountType accountType,
        @NotNull Long glAccountId,
        @Size(max = 200) String bankName,
        @Size(max = 50) String bankAccountNo) {
}
