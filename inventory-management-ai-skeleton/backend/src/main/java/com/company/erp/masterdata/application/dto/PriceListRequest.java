package com.company.erp.masterdata.application.dto;

import com.company.erp.masterdata.domain.PriceTargetType;
import com.company.erp.masterdata.domain.PriceType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

public record PriceListRequest(
        @NotNull Long companyId,
        @NotBlank @Pattern(regexp = "[A-Z0-9_.-]{1,30}", message = "Mã gồm 1-30 ký tự A-Z, 0-9, _ . -") String code,
        @NotBlank @Size(max = 200) String name,
        @NotNull PriceType priceType,
        @NotNull PriceTargetType targetType,
        Long partnerGroupId,
        Long partnerId,
        @Pattern(regexp = "[A-Z]{3}", message = "Mã tiền tệ gồm 3 chữ in hoa") String currencyCode,
        @NotNull LocalDate validFrom,
        LocalDate validTo) {
}
