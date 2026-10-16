package com.company.erp.masterdata.domain;

import com.company.erp.common.domain.BusinessException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Set;

/**
 * Hàng hóa (BR-MD-01..09). Mã duy nhất trong công ty. Loại hàng, đơn vị cơ sở, cờ lô/hạn/serial không đổi sau khi
 * phát sinh giao dịch kho (BR-MD-04); kiểm tra "đã phát sinh" ở tầng application.
 */
@Entity
@Table(name = "products")
public class Product extends CodeNameEntity {

    private static final Set<String> DEVICE_CLASSES = Set.of("A", "B", "C", "D");

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "legacy_code", length = 50)
    private String legacyCode;

    @Column(name = "trade_name")
    private String tradeName;

    @Column(name = "category_id", nullable = false)
    private Long categoryId;

    @Enumerated(EnumType.STRING)
    @Column(name = "product_type", nullable = false, length = 20)
    private ProductType productType;

    @Column(name = "base_unit_id", nullable = false)
    private Long baseUnitId;

    @Column(name = "brand_id")
    private Long brandId;

    @Column(name = "manufacturer_id")
    private Long manufacturerId;

    @Column(name = "origin_country_code", length = 2)
    private String originCountryCode;

    @Column(name = "model", length = 100)
    private String model;

    @Column(name = "specification", length = 500)
    private String specification;

    @Column(name = "description", length = 2000)
    private String description;

    @Column(name = "lot_tracked", nullable = false)
    private boolean lotTracked;

    @Column(name = "expiry_tracked", nullable = false)
    private boolean expiryTracked;

    @Column(name = "serial_tracked", nullable = false)
    private boolean serialTracked;

    @Column(name = "medical_device_class", length = 1)
    private String medicalDeviceClass;

    @Column(name = "registration_no", length = 100)
    private String registrationNo;

    @Column(name = "registration_holder")
    private String registrationHolder;

    @Column(name = "registration_valid_to")
    private LocalDate registrationValidTo;

    @Column(name = "storage_min_temp", precision = 5, scale = 2)
    private BigDecimal storageMinTemp;

    @Column(name = "storage_max_temp", precision = 5, scale = 2)
    private BigDecimal storageMaxTemp;

    @Column(name = "cold_chain", nullable = false)
    private boolean coldChain;

    @Column(name = "hazardous", nullable = false)
    private boolean hazardous;

    @Column(name = "warranty_months")
    private Integer warrantyMonths;

    @Column(name = "maintenance_cycle_months")
    private Integer maintenanceCycleMonths;

    @Column(name = "requires_installation", nullable = false)
    private boolean requiresInstallation;

    @Column(name = "requires_calibration", nullable = false)
    private boolean requiresCalibration;

    @Column(name = "slow_moving_days")
    private Integer slowMovingDays;

    @Column(name = "default_tax_rate_id")
    private Long defaultTaxRateId;

    protected Product() {
    }

    public Product(Long companyId, String code, ProductType productType, Long baseUnitId, boolean lotTracked,
                   boolean expiryTracked, boolean serialTracked, ProductDetails details) {
        super(companyId, code, details.name());
        this.productType = productType;
        this.baseUnitId = baseUnitId;
        applyTracking(lotTracked, expiryTracked, serialTracked);
        update(details);
    }

    public final void update(ProductDetails details) {
        if (details.medicalDeviceClass() != null && !DEVICE_CLASSES.contains(details.medicalDeviceClass())) {
            throw BusinessException.invalidField("medicalDeviceClass", "Phân loại TTBYT chỉ gồm A, B, C, D.");
        }
        if (details.storageMinTemp() != null && details.storageMaxTemp() != null
                && details.storageMinTemp().compareTo(details.storageMaxTemp()) > 0) {
            throw BusinessException.invalidField("storageMaxTemp", "Nhiệt độ tối đa phải lớn hơn tối thiểu.");
        }
        rename(details.name());
        this.legacyCode = details.legacyCode();
        this.tradeName = details.tradeName();
        this.categoryId = details.categoryId();
        this.brandId = details.brandId();
        this.manufacturerId = details.manufacturerId();
        this.originCountryCode = details.originCountryCode();
        this.model = details.model();
        this.specification = details.specification();
        this.description = details.description();
        this.medicalDeviceClass = details.medicalDeviceClass();
        this.registrationNo = details.registrationNo();
        this.registrationHolder = details.registrationHolder();
        this.registrationValidTo = details.registrationValidTo();
        this.storageMinTemp = details.storageMinTemp();
        this.storageMaxTemp = details.storageMaxTemp();
        this.coldChain = details.coldChain();
        this.hazardous = details.hazardous();
        this.warrantyMonths = details.warrantyMonths();
        this.maintenanceCycleMonths = details.maintenanceCycleMonths();
        this.requiresInstallation = details.requiresInstallation();
        this.requiresCalibration = details.requiresCalibration();
        this.slowMovingDays = details.slowMovingDays();
        this.defaultTaxRateId = details.defaultTaxRateId();
    }

    /** Đổi loại hàng, đơn vị cơ sở, cờ quản lý; chỉ khi chưa phát sinh giao dịch kho (kiểm tra ở application). */
    public void changeStructure(ProductType productType, Long baseUnitId, boolean lotTracked, boolean expiryTracked,
                                boolean serialTracked) {
        this.productType = productType;
        this.baseUnitId = baseUnitId;
        applyTracking(lotTracked, expiryTracked, serialTracked);
    }

    public boolean hasSameStructure(ProductType productType, Long baseUnitId, boolean lotTracked,
                                    boolean expiryTracked, boolean serialTracked) {
        return this.productType == productType && this.baseUnitId.equals(baseUnitId) && this.lotTracked == lotTracked
                && this.expiryTracked == expiryTracked && this.serialTracked == serialTracked;
    }

    private void applyTracking(boolean lot, boolean expiry, boolean serial) {
        if (productType == ProductType.SERVICE && (lot || expiry || serial)) {
            throw BusinessException.invalidField("productType", "Hàng dịch vụ không quản lý tồn, không theo lô/hạn/serial.");
        }
        if (expiry && !lot) {
            throw BusinessException.invalidField("expiryTracked", "Quản lý hạn dùng phải quản lý theo lô (BR-INV-01).");
        }
        this.lotTracked = lot;
        this.expiryTracked = expiry;
        this.serialTracked = serial;
    }

    public boolean isStockTracked() {
        return productType != ProductType.SERVICE;
    }

    @Override
    public Long getId() {
        return id;
    }

    public String getLegacyCode() {
        return legacyCode;
    }

    public String getTradeName() {
        return tradeName;
    }

    public Long getCategoryId() {
        return categoryId;
    }

    public ProductType getProductType() {
        return productType;
    }

    public Long getBaseUnitId() {
        return baseUnitId;
    }

    public Long getBrandId() {
        return brandId;
    }

    public Long getManufacturerId() {
        return manufacturerId;
    }

    public String getOriginCountryCode() {
        return originCountryCode;
    }

    public String getModel() {
        return model;
    }

    public String getSpecification() {
        return specification;
    }

    public String getDescription() {
        return description;
    }

    public boolean isLotTracked() {
        return lotTracked;
    }

    public boolean isExpiryTracked() {
        return expiryTracked;
    }

    public boolean isSerialTracked() {
        return serialTracked;
    }

    public String getMedicalDeviceClass() {
        return medicalDeviceClass;
    }

    public String getRegistrationNo() {
        return registrationNo;
    }

    public String getRegistrationHolder() {
        return registrationHolder;
    }

    public LocalDate getRegistrationValidTo() {
        return registrationValidTo;
    }

    public BigDecimal getStorageMinTemp() {
        return storageMinTemp;
    }

    public BigDecimal getStorageMaxTemp() {
        return storageMaxTemp;
    }

    public boolean isColdChain() {
        return coldChain;
    }

    public boolean isHazardous() {
        return hazardous;
    }

    public Integer getWarrantyMonths() {
        return warrantyMonths;
    }

    public Integer getMaintenanceCycleMonths() {
        return maintenanceCycleMonths;
    }

    public boolean isRequiresInstallation() {
        return requiresInstallation;
    }

    public boolean isRequiresCalibration() {
        return requiresCalibration;
    }

    public Integer getSlowMovingDays() {
        return slowMovingDays;
    }

    public Long getDefaultTaxRateId() {
        return defaultTaxRateId;
    }
}
