package com.company.erp.masterdata.application;

import com.company.erp.masterdata.application.dto.ProductRequest;
import com.company.erp.masterdata.application.dto.ProductResponse;
import com.company.erp.masterdata.application.dto.ProductUnitResponse;
import com.company.erp.masterdata.application.dto.StockLevelDto;
import com.company.erp.masterdata.application.dto.UpdateProductRequest;
import com.company.erp.masterdata.domain.Product;
import com.company.erp.masterdata.domain.ProductDetails;
import com.company.erp.masterdata.domain.ProductUnit;
import java.util.List;

/** Chuyển giữa DTO và entity hàng hóa. */
final class ProductMapper {

    private ProductMapper() {
    }

    static ProductDetails details(ProductRequest request) {
        return new ProductDetails(trim(request.legacyCode()), request.name().trim(), trim(request.tradeName()),
                request.categoryId(), request.brandId(), request.manufacturerId(), request.originCountryCode(),
                trim(request.model()), trim(request.specification()), trim(request.description()),
                request.medicalDeviceClass(), trim(request.registrationNo()), trim(request.registrationHolder()),
                request.registrationValidTo(), request.storageMinTemp(), request.storageMaxTemp(),
                request.coldChain(), request.hazardous(), request.warrantyMonths(), request.maintenanceCycleMonths(),
                request.requiresInstallation(), request.requiresCalibration(), request.slowMovingDays(),
                request.defaultTaxRateId());
    }

    static ProductDetails details(UpdateProductRequest request) {
        return new ProductDetails(trim(request.legacyCode()), request.name().trim(), trim(request.tradeName()),
                request.categoryId(), request.brandId(), request.manufacturerId(), request.originCountryCode(),
                trim(request.model()), trim(request.specification()), trim(request.description()),
                request.medicalDeviceClass(), trim(request.registrationNo()), trim(request.registrationHolder()),
                request.registrationValidTo(), request.storageMinTemp(), request.storageMaxTemp(),
                request.coldChain(), request.hazardous(), request.warrantyMonths(), request.maintenanceCycleMonths(),
                request.requiresInstallation(), request.requiresCalibration(), request.slowMovingDays(),
                request.defaultTaxRateId());
    }

    static ProductResponse toResponse(Product product, boolean structureLocked, List<ProductUnitResponse> units,
                                      List<StockLevelDto> stockLevels) {
        return new ProductResponse(product.getId(), product.getCompanyId(), product.getCode(), product.getLegacyCode(),
                product.getName(), product.getTradeName(), product.getCategoryId(), product.getProductType(),
                product.getBaseUnitId(), product.getBrandId(), product.getManufacturerId(),
                product.getOriginCountryCode(), product.getModel(), product.getSpecification(),
                product.getDescription(), product.isLotTracked(), product.isExpiryTracked(), product.isSerialTracked(),
                product.initialQualityStatus(), product.getMedicalDeviceClass(), product.getRegistrationNo(),
                product.getRegistrationHolder(), product.getRegistrationValidTo(), product.getStorageMinTemp(),
                product.getStorageMaxTemp(), product.isColdChain(), product.isHazardous(), product.getWarrantyMonths(),
                product.getMaintenanceCycleMonths(), product.isRequiresInstallation(),
                product.isRequiresCalibration(), product.getSlowMovingDays(), product.getDefaultTaxRateId(),
                product.getStatus(), structureLocked, units, stockLevels, product.getVersion(),
                product.getUpdatedAt());
    }

    static ProductUnitResponse toResponse(ProductUnit unit) {
        return new ProductUnitResponse(unit.getId(), unit.getUnitId(), unit.getConversionFactor(),
                unit.getEffectiveFrom(), unit.getStatus(), unit.getVersion());
    }

    static String trim(String text) {
        return text == null || text.isBlank() ? null : text.trim();
    }
}
