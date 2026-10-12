package com.company.erp.masterdata.application;

import com.company.erp.common.domain.BusinessException;
import com.company.erp.common.domain.QualityStatus;
import com.company.erp.common.domain.RecordStatus;
import com.company.erp.masterdata.domain.LocationZone;
import com.company.erp.masterdata.domain.Partner;
import com.company.erp.masterdata.domain.PartnerRole;
import com.company.erp.masterdata.domain.Product;
import com.company.erp.masterdata.domain.ProductType;
import com.company.erp.masterdata.domain.ReferenceCategory;
import com.company.erp.masterdata.domain.VirtualWarehouseKind;
import com.company.erp.masterdata.domain.Warehouse;
import com.company.erp.masterdata.domain.WarehouseLocation;
import com.company.erp.masterdata.domain.WarehouseType;
import com.company.erp.masterdata.infrastructure.persistence.PartnerRepository;
import com.company.erp.masterdata.infrastructure.persistence.ProductRepository;
import com.company.erp.masterdata.infrastructure.persistence.ProductStockLevelRepository;
import com.company.erp.masterdata.infrastructure.persistence.ProductUnitRepository;
import com.company.erp.masterdata.infrastructure.persistence.ReferenceItemRepository;
import com.company.erp.masterdata.infrastructure.persistence.TaxRateRepository;
import com.company.erp.masterdata.infrastructure.persistence.WarehouseLocationRepository;
import com.company.erp.masterdata.infrastructure.persistence.WarehouseRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * API công khai của module danh mục cho module nghiệp vụ khác (kho, mua, bán...): tra cứu hàng, đơn vị quy đổi, kho,
 * vị trí, đối tác, lý do. Trả record bất biến, không lộ entity (architecture.md mục 3).
 */
@Service
public class MasterDataQueryService {

    public record ProductInfo(Long id, Long companyId, String code, String name, ProductType productType,
                              Long baseUnitId, boolean lotTracked, boolean expiryTracked, boolean serialTracked,
                              QualityStatus initialQualityStatus, boolean active, boolean stockTracked,
                              Long defaultTaxRateId) {

        /** Mã loại hàng dạng chuỗi (điều kiện quy tắc hạch toán) cho module khác. */
        public String productTypeCode() {
            return productType.name();
        }
    }

    public record WarehouseInfo(Long id, Long companyId, Long branchId, String code, String name,
                                WarehouseType warehouseType, VirtualWarehouseKind virtualKind, boolean active) {

        /** Kho vật lý (không phải kho ảo), cho module khác kiểm tra mà không phụ thuộc enum của danh mục. */
        public boolean physical() {
            return warehouseType == WarehouseType.NORMAL;
        }
    }

    public record LocationInfo(Long id, Long warehouseId, String code, String name, LocationZone zoneType,
                               boolean defaultLocation, boolean active) {
    }

    /**
     * Đối tác; {@code paymentTermDays} / {@code supplierPaymentTermDays}: số ngày nợ khi là khách hàng / nhà cung cấp
     * (BR-AR-02), {@code null} khi chưa khai báo; {@code taxCode}: mã số thuế.
     */
    public record PartnerInfo(Long id, Long companyId, String code, String name, Set<PartnerRole> roles,
                              Long salesUserId, boolean active, Integer paymentTermDays,
                              Integer supplierPaymentTermDays, String taxCode) {

        /** Mã vai trò dạng chuỗi, để module khác kiểm tra mà không phụ thuộc enum của danh mục. */
        public Set<String> roleCodes() {
            return roles.stream().map(Enum::name).collect(Collectors.toSet());
        }
    }

    /** Thuế suất (%) có hiệu lực từ {@code validFrom} đến {@code validTo} ({@code null} = không thời hạn). */
    public record CustomerProfile(Long id, Long companyId, String code, String name, boolean active,
                                  Set<String> roleCodes, Long partnerGroupId, Long priceListId,
                                  BigDecimal creditLimit, Integer paymentTermDays, Long salesUserId, String taxCode,
                                  String address, BigDecimal agentDiscountPercent,
                                  Integer minRemainingShelfLifeDays) {
    }

    public record ProductDocumentInfo(Long id, String originCountryCode, String model, String specification,
                                      String registrationNo, Integer warrantyMonths) {
    }

    public record TaxRateInfo(Long id, Long companyId, String code, String name, BigDecimal rate, LocalDate validFrom,
                              LocalDate validTo, boolean active) {

        public boolean effectiveOn(LocalDate date) {
            return active && !date.isBefore(validFrom) && (validTo == null || !date.isAfter(validTo));
        }
    }

    public record StockLevelInfo(Long productId, Long warehouseId, BigDecimal minQuantity, BigDecimal maxQuantity) {
    }

    public record ReferenceInfo(Long id, Long companyId, ReferenceCategory category, String code, String name,
                                boolean active) {

        /** Mã nhóm danh mục dạng chuỗi cho module khác (không phụ thuộc enum domain của danh mục). */
        public String categoryCode() {
            return category.name();
        }
    }

    private final ProductRepository productRepository;
    private final ProductUnitRepository productUnitRepository;
    private final ProductStockLevelRepository stockLevelRepository;
    private final WarehouseRepository warehouseRepository;
    private final WarehouseLocationRepository locationRepository;
    private final PartnerRepository partnerRepository;
    private final ReferenceItemRepository referenceItemRepository;
    private final TaxRateRepository taxRateRepository;

    public MasterDataQueryService(ProductRepository productRepository, ProductUnitRepository productUnitRepository,
                                  ProductStockLevelRepository stockLevelRepository,
                                  WarehouseRepository warehouseRepository,
                                  WarehouseLocationRepository locationRepository, PartnerRepository partnerRepository,
                                  ReferenceItemRepository referenceItemRepository,
                                  TaxRateRepository taxRateRepository) {
        this.productRepository = productRepository;
        this.productUnitRepository = productUnitRepository;
        this.stockLevelRepository = stockLevelRepository;
        this.warehouseRepository = warehouseRepository;
        this.locationRepository = locationRepository;
        this.partnerRepository = partnerRepository;
        this.referenceItemRepository = referenceItemRepository;
        this.taxRateRepository = taxRateRepository;
    }

    /** Mã loại hàng hợp lệ, dùng làm điều kiện quy tắc hạch toán (BR-ACC-02). */
    public static boolean isProductType(String code) {
        return Arrays.stream(ProductType.values()).anyMatch(type -> type.name().equals(code));
    }

    /** Mã vai trò đối tác hợp lệ, dùng làm điều kiện quy tắc hạch toán (BR-ACC-02). */
    public static boolean isPartnerRole(String code) {
        return Arrays.stream(PartnerRole.values()).anyMatch(role -> role.name().equals(code));
    }

    @Transactional(readOnly = true)
    public Optional<ProductInfo> product(Long id) {
        return productRepository.findById(id).map(MasterDataQueryService::toInfo);
    }

    @Transactional(readOnly = true)
    public List<ProductInfo> products(Collection<Long> ids) {
        return productRepository.findAllById(ids).stream().map(MasterDataQueryService::toInfo).toList();
    }

    /**
     * Hệ số quy đổi đơn vị giao dịch về đơn vị cơ sở tại ngày chứng từ (BR-MD-20, BR-MD-21). Đơn vị cơ sở: 1.
     * Đơn vị chưa khai báo hoặc chưa có hiệu lực tại ngày: lỗi trên trường {@code unitId}.
     */
    @Transactional(readOnly = true)
    public BigDecimal conversionFactor(Long productId, Long unitId, LocalDate date) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> BusinessException.invalidField("productId", "Hàng hóa không tồn tại."));
        if (product.getBaseUnitId().equals(unitId)) {
            return BigDecimal.ONE;
        }
        return productUnitRepository
                .findFirstByProductIdAndUnitIdAndEffectiveFromLessThanEqualAndStatusOrderByEffectiveFromDesc(
                        productId, unitId, date, RecordStatus.ACTIVE)
                .map(unit -> unit.getConversionFactor())
                .orElseThrow(() -> BusinessException.invalidField("unitId",
                        "Đơn vị tính chưa khai báo hệ số quy đổi có hiệu lực cho hàng " + product.getCode() + "."));
    }

    @Transactional(readOnly = true)
    public Optional<WarehouseInfo> warehouse(Long id) {
        return warehouseRepository.findById(id).map(MasterDataQueryService::toInfo);
    }

    @Transactional(readOnly = true)
    public List<WarehouseInfo> warehouses(Collection<Long> ids) {
        return warehouseRepository.findByIdIn(ids).stream().map(MasterDataQueryService::toInfo).toList();
    }

    /** Mọi kho (kể cả ngừng hoạt động) của các chi nhánh; dùng để lọc phạm vi dữ liệu theo kho. */
    @Transactional(readOnly = true)
    public List<WarehouseInfo> warehousesOfBranches(Collection<Long> branchIds) {
        if (branchIds.isEmpty()) {
            return List.of();
        }
        return warehouseRepository.findByBranchIdIn(branchIds).stream().map(MasterDataQueryService::toInfo).toList();
    }

    /** Kho ảo đang hoạt động theo loại của chi nhánh (ví dụ kho "đang vận chuyển" khi chuyển khác chi nhánh). */
    @Transactional(readOnly = true)
    public Optional<WarehouseInfo> virtualWarehouse(Long branchId, VirtualWarehouseKind kind) {
        return warehouseRepository.findAll((root, query, cb) -> cb.and(
                        cb.equal(root.get("branchId"), branchId),
                        cb.equal(root.get("virtualKind"), kind),
                        cb.equal(root.get("status"), RecordStatus.ACTIVE)))
                .stream().min((first, second) -> first.getId().compareTo(second.getId()))
                .map(MasterDataQueryService::toInfo);
    }

    /** Định mức tồn tối thiểu / tối đa đã khai báo của các kho (cảnh báo tồn dưới định mức, BR-INV-40). */
    @Transactional(readOnly = true)
    public List<StockLevelInfo> stockLevels(Collection<Long> warehouseIds) {
        if (warehouseIds.isEmpty()) {
            return List.of();
        }
        return stockLevelRepository.findByWarehouseIdInAndMinQuantityIsNotNull(warehouseIds).stream()
                .map(level -> new StockLevelInfo(level.getProductId(), level.getWarehouseId(), level.getMinQuantity(),
                        level.getMaxQuantity()))
                .toList();
    }

    /** Kho "đang vận chuyển" của chi nhánh (chuyển kho khác chi nhánh, BR-INV-30). */
    @Transactional(readOnly = true)
    public Optional<WarehouseInfo> inTransitWarehouse(Long branchId) {
        return virtualWarehouse(branchId, VirtualWarehouseKind.IN_TRANSIT);
    }

    @Transactional(readOnly = true)
    public Optional<LocationInfo> location(Long id) {
        return locationRepository.findById(id).map(MasterDataQueryService::toInfo);
    }

    @Transactional(readOnly = true)
    public List<LocationInfo> locations(Collection<Long> ids) {
        return locationRepository.findByIdIn(ids).stream().map(MasterDataQueryService::toInfo).toList();
    }

    @Transactional(readOnly = true)
    public Optional<LocationInfo> defaultLocation(Long warehouseId) {
        return locationRepository.findByWarehouseIdAndDefaultLocationTrue(warehouseId)
                .map(MasterDataQueryService::toInfo);
    }

    @Transactional(readOnly = true)
    public Optional<PartnerInfo> partner(Long id) {
        return partnerRepository.findById(id).map(MasterDataQueryService::toInfo);
    }

    @Transactional(readOnly = true)
    public List<PartnerInfo> partners(Collection<Long> ids) {
        return partnerRepository.findAllById(ids).stream().map(MasterDataQueryService::toInfo).toList();
    }

    /**
     * Hồ sơ bán hàng của khách: nhóm, bảng giá riêng, hạn mức tín dụng (đại lý: hạn mức đại lý nếu có), hạn thanh
     * toán, nhân viên phụ trách, chiết khấu đại lý, hạn dùng còn lại tối thiểu (D-07).
     */
    @Transactional(readOnly = true)
    public Optional<CustomerProfile> customerProfile(Long id) {
        return partnerRepository.findById(id).map(partner -> new CustomerProfile(partner.getId(),
                partner.getCompanyId(), partner.getCode(), partner.getName(), partner.isActive(),
                partner.getRoles().stream().map(Enum::name).collect(Collectors.toSet()), partner.getPartnerGroupId(),
                partner.getPriceListId(), partner.getAgentCreditLimit() != null
                && partner.getRoles().contains(PartnerRole.AGENT) ? partner.getAgentCreditLimit()
                : partner.getCreditLimit(), partner.getPaymentTermDays(), partner.getSalesUserId(),
                partner.getTaxCode(), partner.getAddress(), partner.getAgentDiscountPercent(),
                partner.getMinRemainingShelfLifeDays()));
    }

    /** Thông tin hồ sơ hàng cho báo giá / biên bản giao nhận (xuất xứ, số lưu hành, quy cách, bảo hành). */
    @Transactional(readOnly = true)
    public List<ProductDocumentInfo> productDocuments(Collection<Long> ids) {
        return productRepository.findAllById(ids).stream()
                .map(product -> new ProductDocumentInfo(product.getId(), product.getOriginCountryCode(),
                        product.getModel(), product.getSpecification(), product.getRegistrationNo(),
                        product.getWarrantyMonths()))
                .toList();
    }

    @Transactional(readOnly = true)
    public Optional<ReferenceInfo> referenceItem(Long id) {
        return referenceItemRepository.findById(id).map(item -> new ReferenceInfo(item.getId(), item.getCompanyId(),
                item.getCategory(), item.getCode(), item.getName(), item.isActive()));
    }

    @Transactional(readOnly = true)
    public Optional<TaxRateInfo> taxRate(Long id) {
        return taxRateRepository.findById(id).map(rate -> new TaxRateInfo(rate.getId(), rate.getCompanyId(),
                rate.getCode(), rate.getName(), rate.getRate(), rate.getValidFrom(), rate.getValidTo(),
                rate.isActive()));
    }

    private static ProductInfo toInfo(Product product) {
        return new ProductInfo(product.getId(), product.getCompanyId(), product.getCode(), product.getName(),
                product.getProductType(), product.getBaseUnitId(), product.isLotTracked(), product.isExpiryTracked(),
                product.isSerialTracked(), product.initialQualityStatus(), product.isActive(),
                product.isStockTracked(), product.getDefaultTaxRateId());
    }

    private static WarehouseInfo toInfo(Warehouse warehouse) {
        return new WarehouseInfo(warehouse.getId(), warehouse.getCompanyId(), warehouse.getBranchId(),
                warehouse.getCode(), warehouse.getName(), warehouse.getWarehouseType(), warehouse.getVirtualKind(),
                warehouse.isActive());
    }

    private static LocationInfo toInfo(WarehouseLocation location) {
        return new LocationInfo(location.getId(), location.getWarehouseId(), location.getCode(), location.getName(),
                location.getZoneType(), location.isDefaultLocation(), location.getStatus() == RecordStatus.ACTIVE);
    }

    private static PartnerInfo toInfo(Partner partner) {
        return new PartnerInfo(partner.getId(), partner.getCompanyId(), partner.getCode(), partner.getName(),
                partner.getRoles(), partner.getSalesUserId(), partner.isActive(), partner.getPaymentTermDays(),
                partner.getSupplierPaymentTermDays(), partner.getTaxCode());
    }
}
