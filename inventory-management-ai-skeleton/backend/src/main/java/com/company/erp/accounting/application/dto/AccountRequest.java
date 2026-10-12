package com.company.erp.accounting.application.dto;

import com.company.erp.accounting.domain.BalanceSide;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record AccountRequest(
        @NotNull Long companyId,
        @NotBlank @Pattern(regexp = "[0-9A-Z_.-]{1,20}", message = "Mã gồm 1-20 ký tự 0-9, A-Z, _ . -") String code,
        @NotBlank @Size(max = 255) String name,
        Long parentId,
        @NotNull BalanceSide balanceSide,
        boolean partnerRequired) {
}
