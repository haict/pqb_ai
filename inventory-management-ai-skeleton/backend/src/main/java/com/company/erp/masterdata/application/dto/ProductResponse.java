package com.company.erp.masterdata.application.dto;

import com.company.erp.common.domain.QualityStatus;
import com.company.erp.common.domain.RecordStatus;
import com.company.erp.masterdata.domain.ProductType;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** Hàng hóa; {@code units}, {@code stockLevels} chỉ có ở API chi tiết (danh sách trả rỗng). */
public record ProductResponse(
        Long id,
        Long companyId,
        String code,
        String legacyCode,
        String name,
        String tradeName,
        Long categoryId,
        ProductType productType,
        Long baseUnitId,
        Long brandId,
        Long manufacturerId,
        String originCountryCode,
        String model,
        String specification,
        String description,
        boolean lotTracked,
        boolean expiryTracked,
        boolean serialTracked,
        QualityStatus initialQualityStatus,
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
        Long defaultTaxRateId,
        RecordStatus status,
        boolean structureLocked,
        List<ProductUnitResponse> units,
        List<StockLevelDto> stockLevels,
        int version,
        Instant updatedAt) {
}
