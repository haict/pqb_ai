package com.company.erp.masterdata.application;

import com.company.erp.common.api.PageRequests;
import com.company.erp.common.application.IdempotencyService;
import com.company.erp.common.application.IdempotentCommand;
import com.company.erp.common.domain.BusinessException;
import com.company.erp.common.domain.ErrorCode;
import com.company.erp.common.domain.RecordStatus;
import com.company.erp.common.infrastructure.persistence.Specs;
import com.company.erp.masterdata.application.dto.ProductRequest;
import com.company.erp.masterdata.application.dto.ProductResponse;
import com.company.erp.masterdata.application.dto.ProductUnitRequest;
import com.company.erp.masterdata.application.dto.ProductUnitResponse;
import com.company.erp.masterdata.application.dto.StockLevelDto;
import com.company.erp.masterdata.application.dto.StockLevelsRequest;
import com.company.erp.masterdata.application.dto.UpdateProductRequest;
import com.company.erp.masterdata.domain.Product;
import com.company.erp.masterdata.domain.ProductDetails;
import com.company.erp.masterdata.domain.ProductStockLevel;
import com.company.erp.masterdata.domain.ProductType;
import com.company.erp.masterdata.domain.ProductUnit;
import com.company.erp.masterdata.domain.Warehouse;
import com.company.erp.masterdata.infrastructure.persistence.ProductRepository;
import com.company.erp.masterdata.infrastructure.persistence.ProductStockLevelRepository;
import com.company.erp.masterdata.infrastructure.persistence.ProductUnitRepository;
import com.company.erp.masterdata.infrastructure.persistence.WarehouseRepository;
import com.company.erp.system.application.CurrentUser;
import com.company.erp.system.application.dto.ChangeStatusRequest;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Hàng hóa (BR-MD-01..09, BR-MD-12, BR-MD-20, BR-MD-21). Loại hàng, đơn vị cơ sở, cờ lô/hạn/serial không đổi sau khi
 * phát sinh giao dịch kho; hệ số quy đổi đã tạo không sửa, đổi bằng dòng mới có hiệu lực từ ngày khác.
 */
@Service
public class ProductService {

    private static final Set<String> SORTABLE = Set.of("code", "name", "productType", "updatedAt");

    private final ProductRepository productRepository;
    private final ProductUnitRepository unitRepository;
    private final ProductStockLevelRepository stockLevelRepository;
    private final WarehouseRepository warehouseRepository;
    private final ProductReferences references;
    private final ObjectProvider<ProductUsageProbe> usageProbes;
    private final MasterDataGuard guard;
    private final IdempotencyService idempotency;

    public ProductService(ProductRepository productRepository, ProductUnitRepository unitRepository,
                          ProductStockLevelRepository stockLevelRepository, WarehouseRepository warehouseRepository,
                          ProductReferences references, ObjectProvider<ProductUsageProbe> usageProbes,
                          MasterDataGuard guard, IdempotencyService idempotency) {
        this.productRepository = productRepository;
        this.unitRepository = unitRepository;
        this.stockLevelRepository = stockLevelRepository;
        this.warehouseRepository = warehouseRepository;
        this.references = references;
        this.usageProbes = usageProbes;
        this.guard = guard;
        this.idempotency = idempotency;
    }

    @Transactional(readOnly = true)
    public Page<ProductResponse> search(CurrentUser actor, Long companyId, String keyword, Long categoryId,
                                        ProductType productType, RecordStatus status, Pageable pageable) {
        guard.requireCompany(actor, companyId);
        Page<Product> page = productRepository.findAll(Specs.allOf(
                        Specs.<Product>equalsIfPresent("companyId", companyId),
                        Specs.keyword(keyword, "code", "name", "legacyCode", "model"),
                        Specs.equalsIfPresent("categoryId", categoryId),
                        Specs.equalsIfPresent("productType", productType),
                        Specs.equalsIfPresent("status", status)),
                PageRequests.checked(pageable, SORTABLE, Sort.by("code")));
        Set<Long> used = usedProducts(page.getContent().stream().map(Product::getId).collect(Collectors.toSet()));
        return page.map(product -> ProductMapper.toResponse(product, used.contains(product.getId()), List.of(),
                List.of()));
    }

    @Transactional(readOnly = true)
    public ProductResponse get(CurrentUser actor, Long id) {
        return detailed(guard.visible(actor, productRepository.findById(id)));
    }

    @Transactional
    public ProductResponse create(CurrentUser actor, String idempotencyKey, ProductRequest request) {
        IdempotentCommand command = new IdempotentCommand(actor.id(), idempotencyKey, "POST /api/v1/products", request);
        return idempotency.execute(command, ProductResponse.class, () -> {
            guard.requireCompany(actor, request.companyId());
            guard.requireNewCode(productRepository.existsByCompanyIdAndCode(request.companyId(), request.code()),
                    "Hàng hóa", request.code());
            ProductDetails details = ProductMapper.details(request);
            references.validate(request.companyId(), request.baseUnitId(), details);
            Product product = productRepository.saveAndFlush(new Product(request.companyId(), request.code(),
                    request.productType(), request.baseUnitId(), request.lotTracked(), request.expiryTracked(),
                    request.serialTracked(), details));
            ProductResponse response = detailed(product);
            guard.audit(actor, MasterDataAudit.CREATE, MasterDataAudit.PRODUCT, product.getId(), null, null, response,
                    product.getCompanyId());
            return response;
        });
    }

    @Transactional
    public ProductResponse update(CurrentUser actor, Long id, UpdateProductRequest request) {
        Product product = guard.visible(actor, productRepository.findById(id));
        product.verifyVersion(request.version());
        ProductDetails details = ProductMapper.details(request);
        references.validate(product.getCompanyId(), request.baseUnitId(), details);
        ProductResponse before = detailed(product);
        if (!product.hasSameStructure(request.productType(), request.baseUnitId(), request.lotTracked(),
                request.expiryTracked(), request.serialTracked())) {
            if (!usedProducts(Set.of(id)).isEmpty()) {
                throw new BusinessException(ErrorCode.DATA_IN_USE, "Hàng đã phát sinh giao dịch kho: không đổi loại "
                        + "hàng, đơn vị cơ sở, quản lý lô/hạn dùng/serial (BR-MD-04).");
            }
            product.changeStructure(request.productType(), request.baseUnitId(), request.lotTracked(),
                    request.expiryTracked(), request.serialTracked());
        }
        product.update(details);
        ProductResponse after = detailed(productRepository.saveAndFlush(product));
        guard.audit(actor, MasterDataAudit.UPDATE, MasterDataAudit.PRODUCT, id, null, before, after,
                product.getCompanyId());
        return after;
    }

    /** Ngừng kinh doanh thay vì xóa (BR-MD-12). */
    @Transactional
    public ProductResponse changeStatus(CurrentUser actor, Long id, ChangeStatusRequest request) {
        Product product = guard.visible(actor, productRepository.findById(id));
        product.verifyVersion(request.version());
        ProductResponse before = detailed(product);
        product.changeStatus(request.status());
        ProductResponse after = detailed(productRepository.saveAndFlush(product));
        guard.audit(actor, MasterDataAudit.CHANGE_STATUS, MasterDataAudit.PRODUCT, id, request.reason(), before,
                after, product.getCompanyId());
        return after;
    }

    /** Thêm đơn vị giao dịch / hệ số mới có hiệu lực (BR-MD-21). */
    @Transactional
    public ProductResponse addUnit(CurrentUser actor, Long productId, ProductUnitRequest request) {
        Product product = guard.visible(actor, productRepository.findById(productId));
        if (request.unitId().equals(product.getBaseUnitId())) {
            throw BusinessException.invalidField("unitId", "Đơn vị cơ sở có hệ số 1, không khai báo lại.");
        }
        references.requireUnit(product.getCompanyId(), request.unitId(), "unitId");
        if (unitRepository.existsByProductIdAndUnitIdAndEffectiveFrom(productId, request.unitId(),
                request.effectiveFrom())) {
            throw new BusinessException(ErrorCode.DUPLICATE_CODE,
                    "Đơn vị này đã có hệ số hiệu lực từ ngày đã chọn; hệ số đã tạo không sửa (BR-MD-21).");
        }
        ProductUnit unit = unitRepository.saveAndFlush(new ProductUnit(productId, request.unitId(),
                request.conversionFactor(), request.effectiveFrom()));
        guard.audit(actor, MasterDataAudit.CREATE, MasterDataAudit.PRODUCT_UNIT, unit.getId(), null, null,
                ProductMapper.toResponse(unit), product.getCompanyId());
        return detailed(product);
    }

    @Transactional
    public ProductResponse changeUnitStatus(CurrentUser actor, Long productId, Long unitRowId,
                                            ChangeStatusRequest request) {
        Product product = guard.visible(actor, productRepository.findById(productId));
        ProductUnit unit = unitRepository.findById(unitRowId)
                .filter(found -> found.getProductId().equals(productId))
                .orElseThrow(BusinessException::notFound);
        unit.verifyVersion(request.version());
        ProductUnitResponse before = ProductMapper.toResponse(unit);
        unit.changeStatus(request.status());
        unitRepository.saveAndFlush(unit);
        guard.audit(actor, MasterDataAudit.CHANGE_STATUS, MasterDataAudit.PRODUCT_UNIT, unitRowId, request.reason(),
                before, ProductMapper.toResponse(unit), product.getCompanyId());
        return detailed(product);
    }

    /** Thay định mức tồn theo kho (BR-MD-09). */
    @Transactional
    public ProductResponse replaceStockLevels(CurrentUser actor, Long productId, StockLevelsRequest request) {
        Product product = guard.visible(actor, productRepository.findById(productId));
        Set<Long> warehouseIds = new HashSet<>();
        for (StockLevelDto level : request.levels()) {
            if (!warehouseIds.add(level.warehouseId())) {
                throw BusinessException.invalidField("levels", "Mỗi kho chỉ có một định mức.");
            }
            Warehouse warehouse = warehouseRepository.findById(level.warehouseId())
                    .filter(found -> found.getCompanyId().equals(product.getCompanyId()))
                    .orElseThrow(() -> BusinessException.invalidField("levels", "Kho không thuộc công ty."));
            if (!actor.dataScope().canAccessWarehouse(warehouse.getId(), warehouse.getBranchId())) {
                throw BusinessException.notFound();
            }
        }
        List<StockLevelDto> before = stockLevels(productId);
        stockLevelRepository.deleteAll(stockLevelRepository.findByProductId(productId));
        stockLevelRepository.flush();
        stockLevelRepository.saveAllAndFlush(request.levels().stream()
                .map(level -> new ProductStockLevel(productId, level.warehouseId(), level.minQuantity(),
                        level.maxQuantity()))
                .toList());
        guard.audit(actor, MasterDataAudit.UPDATE, MasterDataAudit.STOCK_LEVEL, productId, null, before,
                request.levels(), product.getCompanyId());
        return detailed(product);
    }

    private ProductResponse detailed(Product product) {
        boolean used = !usedProducts(Set.of(product.getId())).isEmpty();
        List<ProductUnitResponse> units = unitRepository.findByProductIdOrderByUnitIdAscEffectiveFromDesc(
                product.getId()).stream().map(ProductMapper::toResponse).toList();
        return ProductMapper.toResponse(product, used, units, stockLevels(product.getId()));
    }

    private List<StockLevelDto> stockLevels(Long productId) {
        return stockLevelRepository.findByProductId(productId).stream()
                .map(level -> new StockLevelDto(level.getWarehouseId(), level.getMinQuantity(), level.getMaxQuantity()))
                .toList();
    }

    private Set<Long> usedProducts(Set<Long> productIds) {
        if (productIds.isEmpty()) {
            return Set.of();
        }
        Set<Long> used = new HashSet<>();
        usageProbes.orderedStream().forEach(probe -> used.addAll(probe.productsWithTransactions(productIds)));
        return used;
    }
}
