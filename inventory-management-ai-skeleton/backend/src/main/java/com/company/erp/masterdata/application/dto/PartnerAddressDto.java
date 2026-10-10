package com.company.erp.masterdata.application.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PartnerAddressDto(
        @NotBlank @Size(max = 500) String address,
        @Size(max = 200) String receiverName,
        @Size(max = 30) String phone,
        boolean defaultAddress) {
}
