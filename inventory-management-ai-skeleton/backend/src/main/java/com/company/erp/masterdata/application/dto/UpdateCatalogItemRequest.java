package com.company.erp.masterdata.application.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UpdateCatalogItemRequest(
        @NotBlank @Size(max = 200) String name,
        @Pattern(regexp = "[A-Z]{2}", message = "Mã quốc gia gồm 2 chữ in hoa") String countryCode,
        @NotNull Integer version) {
}
