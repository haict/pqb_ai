package com.company.erp.masterdata.application.dto;

import com.company.erp.masterdata.domain.CustomerType;
import com.company.erp.masterdata.domain.PartnerKind;
import com.company.erp.masterdata.domain.PartnerRole;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

/** Thêm đối tác (BR-MD-30..35). */
public record PartnerRequest(
        @NotNull Long companyId,
        @NotBlank @Pattern(regexp = "[A-Za-z0-9_.-]{1,50}", message = "Mã gồm 1-50 ký tự chữ, số, _ . -") String code,
        @Size(max = 50) String legacyCode,
        @NotBlank @Size(max = 255) String name,
        @Size(max = 100) String shortName,
        @NotNull PartnerKind partnerKind,
        @Pattern(regexp = "[0-9]{10}(-[0-9]{3})?", message = "Mã số thuế gồm 10 số hoặc 10 số-3 số") String taxCode,
        @Size(max = 500) String address,
        @Size(max = 30) String phone,
        @Email @Size(max = 255) String email,
        @Pattern(regexp = "[A-Z]{2}", message = "Mã quốc gia gồm 2 chữ in hoa") String countryCode,
        Long partnerGroupId,
        @NotEmpty Set<@NotNull PartnerRole> roles,
        CustomerType customerType,
        Long salesUserId,
        Long managingBranchId,
        @PositiveOrZero Integer paymentTermDays,
        @PositiveOrZero BigDecimal creditLimit,
        @PositiveOrZero Integer minRemainingShelfLifeDays,
        Long priceListId,
        @PositiveOrZero Integer supplierPaymentTermDays,
        @Pattern(regexp = "[A-Z]{3}", message = "Mã tiền tệ gồm 3 chữ in hoa") String currencyCode,
        @Size(max = 500) String distributedBrands,
        @DecimalMin("0") @DecimalMax("100") BigDecimal agentDiscountPercent,
        @Size(max = 200) String agentRegion,
        @PositiveOrZero BigDecimal agentCreditLimit,
        @Size(max = 50) List<@Valid @NotNull PartnerContactDto> contacts,
        @Size(max = 50) List<@Valid @NotNull PartnerAddressDto> addresses) {
}
