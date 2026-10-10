package com.company.erp.masterdata.application.dto;

import com.company.erp.masterdata.domain.PriceTargetType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

public record UpdatePriceListRequest(
        @NotBlank @Size(max = 200) String name,
        @NotNull PriceTargetType targetType,
        Long partnerGroupId,
        Long partnerId,
        @Pattern(regexp = "[A-Z]{3}", message = "Mã tiền tệ gồm 3 chữ in hoa") String currencyCode,
        @NotNull LocalDate validFrom,
        LocalDate validTo,
        @NotNull Integer version) {
}
