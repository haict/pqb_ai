package com.company.erp.masterdata.application.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Sửa nhóm hàng; {@code code} để trống thì giữ mã cũ (client trước D-67 không gửi mã). */
public record UpdateProductCategoryRequest(
        @Pattern(regexp = "[A-Z0-9_.-]{1,30}", message = "Mã gồm 1-30 ký tự A-Z, 0-9, _ . -") String code,
        @NotBlank @Size(max = 200) String name,
        Long parentId,
        @NotNull Integer version) {
}
