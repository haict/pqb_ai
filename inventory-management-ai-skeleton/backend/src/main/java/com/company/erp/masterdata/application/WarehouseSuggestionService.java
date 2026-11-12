package com.company.erp.masterdata.application;

import com.company.erp.common.domain.RecordStatus;
import com.company.erp.masterdata.domain.Product;
import com.company.erp.masterdata.domain.ProductCategory;
import com.company.erp.masterdata.domain.Warehouse;
import com.company.erp.masterdata.domain.WarehouseNameMatcher;
import com.company.erp.masterdata.domain.WarehouseType;
import com.company.erp.masterdata.infrastructure.persistence.ProductCategoryRepository;
import com.company.erp.masterdata.infrastructure.persistence.ProductRepository;
import com.company.erp.masterdata.infrastructure.persistence.WarehouseRepository;
import com.company.erp.system.application.CurrentUser;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Gợi ý kho cho dòng hàng theo tên nhóm hàng (D-76): kho vật lý đang hoạt động của chi nhánh, trong phạm vi người
 * dùng, có tên khớp gần đúng tên nhóm của hàng ({@link WarehouseNameMatcher}); không khớp thì thử nhóm cha. Không tìm
 * được kho duy nhất thì không gợi ý. Dùng chung cho nhập tay, nhập Excel, nhận dạng chứng từ.
 */
@Service
public class WarehouseSuggestionService {

    private final ProductRepository productRepository;
    private final ProductCategoryRepository categoryRepository;
    private final WarehouseRepository warehouseRepository;

    public WarehouseSuggestionService(ProductRepository productRepository,
                                      ProductCategoryRepository categoryRepository,
                                      WarehouseRepository warehouseRepository) {
        this.productRepository = productRepository;
        this.categoryRepository = categoryRepository;
        this.warehouseRepository = warehouseRepository;
    }

    /** Kho gợi ý theo hàng: id hàng → id kho; hàng không có gợi ý không có trong kết quả. */
    @Transactional(readOnly = true)
    public Map<Long, Long> suggest(CurrentUser actor, Long branchId, Collection<Long> productIds) {
        if (branchId == null || productIds.isEmpty()) {
            return Map.of();
        }
        List<Warehouse> usable = warehouseRepository.findByBranchIdIn(List.of(branchId)).stream()
                .filter(warehouse -> warehouse.getWarehouseType() == WarehouseType.NORMAL)
                .filter(warehouse -> warehouse.getStatus() == RecordStatus.ACTIVE)
                .filter(warehouse -> actor.dataScope().canAccessCompany(warehouse.getCompanyId())
                        && actor.dataScope().canAccessWarehouse(warehouse.getId(), warehouse.getBranchId()))
                .toList();
        if (usable.isEmpty()) {
            return Map.of();
        }
        Long companyId = usable.get(0).getCompanyId();
        Map<Long, String> warehouses = usable.stream().collect(Collectors.toMap(Warehouse::getId, Warehouse::getName,
                (first, second) -> first, LinkedHashMap::new));
        List<Product> products = productRepository.findAllById(new HashSet<>(productIds)).stream()
                .filter(product -> product.getCompanyId().equals(companyId) && product.getCategoryId() != null)
                .toList();
        if (products.isEmpty()) {
            return Map.of();
        }
        Map<Long, ProductCategory> categories = categoryRepository.findByCompanyId(companyId).stream()
                .collect(Collectors.toMap(ProductCategory::getId, Function.identity()));
        Map<Long, Optional<Long>> byCategory = new HashMap<>();
        Map<Long, Long> result = new HashMap<>();
        for (Product product : products) {
            byCategory.computeIfAbsent(product.getCategoryId(), id -> match(id, categories, warehouses))
                    .ifPresent(warehouseId -> result.put(product.getId(), warehouseId));
        }
        return result;
    }

    /** Nhóm của hàng rồi lần lượt các nhóm cha; dừng ở nhóm đầu tiên có kho khớp duy nhất. */
    private static Optional<Long> match(Long categoryId, Map<Long, ProductCategory> categories,
                                        Map<Long, String> warehouses) {
        Set<Long> visited = new HashSet<>();
        ProductCategory category = categories.get(categoryId);
        while (category != null && visited.add(category.getId())) {
            Optional<Long> found = WarehouseNameMatcher.match(category.getName(), warehouses);
            if (found.isPresent()) {
                return found;
            }
            category = category.getParentId() == null ? null : categories.get(category.getParentId());
        }
        return Optional.empty();
    }
}
