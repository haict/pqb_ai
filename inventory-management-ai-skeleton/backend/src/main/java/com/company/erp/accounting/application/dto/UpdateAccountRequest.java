package com.company.erp.accounting.application.dto;

import com.company.erp.accounting.domain.BalanceSide;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record UpdateAccountRequest(
        @NotBlank @Size(max = 255) String name,
        @NotNull BalanceSide balanceSide,
        boolean partnerRequired,
        @NotNull Integer version) {
}
