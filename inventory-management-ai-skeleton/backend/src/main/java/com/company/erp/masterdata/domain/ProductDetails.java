package com.company.erp.masterdata.domain;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Thông tin sửa được của hàng hóa (ngoài mã, loại, đơn vị cơ sở, cờ quản lý). */
public record ProductDetails(
        String legacyCode,
        String name,
        String tradeName,
        Long categoryId,
        Long brandId,
        Long manufacturerId,
        String originCountryCode,
        String model,
        String specification,
        String description,
        String medicalDeviceClass,
        String registrationNo,
        String registrationHolder,
        LocalDate registrationValidTo,
        BigDecimal storageMinTemp,
        BigDecimal storageMaxTemp,
        boolean coldChain,
        boolean hazardous,
        Integer warrantyMonths,
        Integer maintenanceCycleMonths,
        boolean requiresInstallation,
        boolean requiresCalibration,
        Integer slowMovingDays,
        Long defaultTaxRateId) {
}
