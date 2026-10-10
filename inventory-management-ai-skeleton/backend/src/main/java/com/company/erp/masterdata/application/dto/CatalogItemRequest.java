package com.company.erp.masterdata.application.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Thêm mục danh mục mã - tên (ĐVT, nhãn hiệu, NSX, nhóm đối tác). {@code countryCode} chỉ dùng cho NSX. */
public record CatalogItemRequest(
        @NotNull Long companyId,
        @NotBlank @Pattern(regexp = "[A-Z0-9_.-]{1,30}", message = "Mã gồm 1-30 ký tự A-Z, 0-9, _ . -") String code,
        @NotBlank @Size(max = 200) String name,
        @Pattern(regexp = "[A-Z]{2}", message = "Mã quốc gia gồm 2 chữ in hoa") String countryCode) {
}
