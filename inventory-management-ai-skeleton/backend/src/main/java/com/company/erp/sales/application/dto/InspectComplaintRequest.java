package com.company.erp.sales.application.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Kết quả kiểm tra hàng lỗi. */
public record InspectComplaintRequest(
        @NotBlank @Size(max = 1000) String result) {
}
