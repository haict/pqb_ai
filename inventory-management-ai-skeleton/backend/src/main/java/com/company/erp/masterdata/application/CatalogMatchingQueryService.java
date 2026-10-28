package com.company.erp.masterdata.application;

import com.company.erp.common.domain.RecordStatus;
import com.company.erp.masterdata.application.MasterDataQueryService.PartnerInfo;
import com.company.erp.masterdata.application.MasterDataQueryService.ProductInfo;
import com.company.erp.masterdata.application.MasterDataQueryService.TaxRateInfo;
import com.company.erp.masterdata.application.MasterDataQueryService.UnitInfo;
import com.company.erp.masterdata.domain.PartnerRole;
import com.company.erp.masterdata.infrastructure.persistence.PartnerRepository;
import com.company.erp.masterdata.infrastructure.persistence.ProductRepository;
import com.company.erp.masterdata.infrastructure.persistence.ProductUnitRepository;
import com.company.erp.masterdata.infrastructure.persistence.TaxRateRepository;
import com.company.erp.masterdata.infrastructure.persistence.UnitRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * API công khai đọc danh mục để đối chiếu dữ liệu nhận dạng từ chứng từ (OCR, D-64): đối tác theo MST, hàng đang hoạt
 * động, đơn vị tính, đơn vị quy đổi, thuế suất của một công ty. Chỉ đọc, trả record bất biến (architecture.md mục 3).
 */
@Service
public class CatalogMatchingQueryService {

    /** Đơn vị giao dịch khác ĐVT cơ sở của hàng, có hiệu lực tại ngày tra cứu. */
    public record ProductUnitInfo(Long productId, Long unitId, BigDecimal conversionFactor) {
    }

    private final PartnerRepository partnerRepository;
    private final ProductRepository productRepository;
    private final ProductUnitRepository productUnitRepository;
    private final UnitRepository unitRepository;
    private final TaxRateRepository taxRateRepository;

    public CatalogMatchingQueryService(PartnerRepository partnerRepository, ProductRepository productRepository,
                                       ProductUnitRepository productUnitRepository, UnitRepository unitRepository,
                                       TaxRateRepository taxRateRepository) {
        this.partnerRepository = partnerRepository;
        this.productRepository = productRepository;
        this.productUnitRepository = productUnitRepository;
        this.unitRepository = unitRepository;
        this.taxRateRepository = taxRateRepository;
    }

    /** Đối tác đang hoạt động có MST đúng bằng {@code taxCode} trong công ty. */
    @Transactional(readOnly = true)
    public List<PartnerInfo> activePartnersByTaxCode(Long companyId, String taxCode) {
        return partnerRepository.findAll((root, query, cb) -> cb.and(
                        cb.equal(root.get("companyId"), companyId),
                        cb.equal(root.get("taxCode"), taxCode),
                        cb.equal(root.get("status"), RecordStatus.ACTIVE)))
                .stream().map(MasterDataQueryService::toInfo).toList();
    }

    /**
     * Đối tác đang hoạt động có vai trò {@code role} (SUPPLIER, CUSTOMER) của công ty, để gợi ý ứng viên theo tên khi
     * chứng từ không in MST (so tên trong bộ nhớ).
     */
    @Transactional(readOnly = true)
    public List<PartnerInfo> activePartnersWithRole(Long companyId, String role) {
        return partnerRepository.findWithRole(companyId, RecordStatus.ACTIVE, PartnerRole.valueOf(role))
                .stream().map(MasterDataQueryService::toInfo).toList();
    }

    /** Mọi hàng đang hoạt động của công ty (so tên hàng trong bộ nhớ). */
    @Transactional(readOnly = true)
    public List<ProductInfo> activeProducts(Long companyId) {
        return productRepository.findAll((root, query, cb) -> cb.and(
                        cb.equal(root.get("companyId"), companyId),
                        cb.equal(root.get("status"), RecordStatus.ACTIVE)))
                .stream().map(MasterDataQueryService::toInfo).toList();
    }

    @Transactional(readOnly = true)
    public List<UnitInfo> activeUnits(Long companyId) {
        return unitRepository.findAll((root, query, cb) -> cb.and(
                        cb.equal(root.get("companyId"), companyId),
                        cb.equal(root.get("status"), RecordStatus.ACTIVE)))
                .stream().map(unit -> new UnitInfo(unit.getId(), unit.getCode(), unit.getName(), true)).toList();
    }

    @Transactional(readOnly = true)
    public List<ProductUnitInfo> productUnits(Collection<Long> productIds, LocalDate date) {
        if (productIds.isEmpty()) {
            return List.of();
        }
        return productUnitRepository.findByProductIdInAndStatusAndEffectiveFromLessThanEqual(productIds,
                        RecordStatus.ACTIVE, date)
                .stream().map(unit -> new ProductUnitInfo(unit.getProductId(), unit.getUnitId(),
                        unit.getConversionFactor()))
                .toList();
    }

    /** Thuế suất của công ty (kể cả hết hiệu lực; bên gọi lọc theo ngày bằng {@link TaxRateInfo#effectiveOn}). */
    @Transactional(readOnly = true)
    public List<TaxRateInfo> taxRates(Long companyId) {
        return taxRateRepository.findAll((root, query, cb) -> cb.equal(root.get("companyId"), companyId))
                .stream().map(MasterDataQueryService::toInfo).toList();
    }
}
