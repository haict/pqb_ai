package com.company.erp.masterdata.application;

import com.company.erp.common.domain.BusinessException;
import com.company.erp.common.domain.RecordStatus;
import com.company.erp.masterdata.domain.ProductDetails;
import com.company.erp.masterdata.infrastructure.persistence.BrandRepository;
import com.company.erp.masterdata.infrastructure.persistence.CountryRepository;
import com.company.erp.masterdata.infrastructure.persistence.ManufacturerRepository;
import com.company.erp.masterdata.infrastructure.persistence.ProductCategoryRepository;
import com.company.erp.masterdata.infrastructure.persistence.TaxRateRepository;
import com.company.erp.masterdata.infrastructure.persistence.UnitRepository;
import org.springframework.stereotype.Component;

/** Kiểm tra danh mục được tham chiếu từ hàng hóa: tồn tại, cùng công ty, đang hoạt động. */
@Component
class ProductReferences {

    private final ProductCategoryRepository categoryRepository;
    private final UnitRepository unitRepository;
    private final BrandRepository brandRepository;
    private final ManufacturerRepository manufacturerRepository;
    private final CountryRepository countryRepository;
    private final TaxRateRepository taxRateRepository;
    private final MasterDataGuard guard;

    ProductReferences(ProductCategoryRepository categoryRepository, UnitRepository unitRepository,
                      BrandRepository brandRepository, ManufacturerRepository manufacturerRepository,
                      CountryRepository countryRepository, TaxRateRepository taxRateRepository,
                      MasterDataGuard guard) {
        this.categoryRepository = categoryRepository;
        this.unitRepository = unitRepository;
        this.brandRepository = brandRepository;
        this.manufacturerRepository = manufacturerRepository;
        this.countryRepository = countryRepository;
        this.taxRateRepository = taxRateRepository;
        this.guard = guard;
    }

    void validate(Long companyId, Long baseUnitId, ProductDetails details) {
        guard.requireActiveReference(categoryRepository.findById(details.categoryId()), companyId, "categoryId",
                "Nhóm hàng");
        requireUnit(companyId, baseUnitId, "baseUnitId");
        if (details.brandId() != null) {
            guard.requireActiveReference(brandRepository.findById(details.brandId()), companyId, "brandId",
                    "Nhãn hiệu");
        }
        if (details.manufacturerId() != null) {
            guard.requireActiveReference(manufacturerRepository.findById(details.manufacturerId()), companyId,
                    "manufacturerId", "Nhà sản xuất");
        }
        if (details.defaultTaxRateId() != null) {
            guard.requireActiveReference(taxRateRepository.findById(details.defaultTaxRateId()), companyId,
                    "defaultTaxRateId", "Thuế suất");
        }
        requireCountry(details.originCountryCode(), "originCountryCode");
    }

    void requireUnit(Long companyId, Long unitId, String field) {
        guard.requireActiveReference(unitRepository.findById(unitId), companyId, field, "Đơn vị tính");
    }

    void requireCountry(String countryCode, String field) {
        if (countryCode == null) {
            return;
        }
        boolean valid = countryRepository.findById(countryCode)
                .filter(country -> country.getStatus() == RecordStatus.ACTIVE)
                .isPresent();
        if (!valid) {
            throw BusinessException.invalidField(field, "Quốc gia không tồn tại hoặc đã ngừng hoạt động.");
        }
    }
}
