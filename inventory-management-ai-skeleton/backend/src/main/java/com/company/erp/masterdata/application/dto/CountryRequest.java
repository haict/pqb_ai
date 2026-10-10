package com.company.erp.masterdata.application.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CountryRequest(
        @NotBlank @Pattern(regexp = "[A-Z]{2}", message = "Mã ISO gồm 2 chữ in hoa") String code,
        @NotBlank @Size(max = 100) String name) {
}
