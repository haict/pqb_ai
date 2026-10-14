package com.company.erp.masterdata.application;

import com.company.erp.masterdata.domain.CodeNameEntity;
import com.company.erp.masterdata.domain.ProductCategory;
import com.company.erp.masterdata.infrastructure.persistence.PartnerGroupRepository;
import com.company.erp.masterdata.infrastructure.persistence.ProductCategoryRepository;
import com.company.erp.masterdata.infrastructure.persistence.UnitRepository;
import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * API công khai tra cứu danh mục mã - tên (đơn vị tính, nhóm hàng, nhóm khách) cho module khác lọc và hiển thị báo cáo.
 * Trả record bất biến, không lộ entity (architecture.md mục 3).
 */
@Service
public class CatalogLookupService {

    public record CatalogItemInfo(Long id, Long companyId, String code, String name, boolean active) {
    }

    private final UnitRepository unitRepository;
    private final ProductCategoryRepository categoryRepository;
    private final PartnerGroupRepository partnerGroupRepository;

    public CatalogLookupService(UnitRepository unitRepository, ProductCategoryRepository categoryRepository,
                                PartnerGroupRepository partnerGroupRepository) {
        this.unitRepository = unitRepository;
        this.categoryRepository = categoryRepository;
        this.partnerGroupRepository = partnerGroupRepository;
    }

    @Transactional(readOnly = true)
    public List<CatalogItemInfo> units(Collection<Long> ids) {
        return unitRepository.findAllById(ids).stream().map(CatalogLookupService::toInfo).toList();
    }

    @Transactional(readOnly = true)
    public List<CatalogItemInfo> productCategories(Collection<Long> ids) {
        return categoryRepository.findAllById(ids).stream().map(CatalogLookupService::toInfo).toList();
    }

    @Transactional(readOnly = true)
    public Optional<CatalogItemInfo> partnerGroup(Long id) {
        return partnerGroupRepository.findById(id).map(CatalogLookupService::toInfo);
    }

    /**
     * Nhóm hàng và mọi nhóm con cháu (BR-MD-03, nhóm dạng cây) của công ty; nhóm không thuộc công ty: tập rỗng.
     * Số nhóm hàng của một công ty nhỏ nên tải một lần rồi duyệt cây trong bộ nhớ.
     */
    @Transactional(readOnly = true)
    public Set<Long> categoryWithDescendants(Long companyId, Long categoryId) {
        List<ProductCategory> categories = categoryRepository.findAll((root, query, cb) ->
                cb.equal(root.get("companyId"), companyId));
        if (categories.stream().noneMatch(category -> category.getId().equals(categoryId))) {
            return Set.of();
        }
        Map<Long, List<Long>> children = categories.stream().filter(category -> category.getParentId() != null)
                .collect(Collectors.groupingBy(ProductCategory::getParentId,
                        Collectors.mapping(ProductCategory::getId, Collectors.toList())));
        Set<Long> result = new HashSet<>();
        Deque<Long> pending = new ArrayDeque<>(List.of(categoryId));
        while (!pending.isEmpty()) {
            Long id = pending.pop();
            if (result.add(id)) {
                pending.addAll(children.getOrDefault(id, List.of()));
            }
        }
        return result;
    }

    private static CatalogItemInfo toInfo(CodeNameEntity entity) {
        return new CatalogItemInfo(entity.getId(), entity.getCompanyId(), entity.getCode(), entity.getName(),
                entity.isActive());
    }
}
