package com.company.erp.masterdata.application.dto;

import com.company.erp.masterdata.domain.ProductType;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;

/** Thêm hàng hóa (BR-MD-01..09). */
public record ProductRequest(
        @NotNull Long companyId,
        @NotBlank @Pattern(regexp = "[A-Za-z0-9_.-]{1,50}", message = "Mã gồm 1-50 ký tự chữ, số, _ . -") String code,
        @Size(max = 50) String legacyCode,
        @NotBlank @Size(max = 255) String name,
        @Size(max = 255) String tradeName,
        @NotNull Long categoryId,
        @NotNull ProductType productType,
        @NotNull Long baseUnitId,
        Long brandId,
        Long manufacturerId,
        @Pattern(regexp = "[A-Z]{2}", message = "Mã quốc gia gồm 2 chữ in hoa") String originCountryCode,
        @Size(max = 100) String model,
        @Size(max = 500) String specification,
        @Size(max = 2000) String description,
        boolean lotTracked,
        boolean expiryTracked,
        boolean serialTracked,
        @Pattern(regexp = "[ABCD]", message = "Phân loại TTBYT gồm A, B, C, D") String medicalDeviceClass,
        @Size(max = 100) String registrationNo,
        @Size(max = 255) String registrationHolder,
        LocalDate registrationValidTo,
        @DecimalMin("-100") @DecimalMax("100") BigDecimal storageMinTemp,
        @DecimalMin("-100") @DecimalMax("100") BigDecimal storageMaxTemp,
        boolean coldChain,
        boolean hazardous,
        @PositiveOrZero Integer warrantyMonths,
        @PositiveOrZero Integer maintenanceCycleMonths,
        boolean requiresInstallation,
        boolean requiresCalibration,
        @Positive Integer slowMovingDays,
        Long defaultTaxRateId) {
}
