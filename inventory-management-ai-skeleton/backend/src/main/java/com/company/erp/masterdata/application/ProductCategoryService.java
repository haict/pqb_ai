package com.company.erp.masterdata.application;

import com.company.erp.common.api.PageRequests;
import com.company.erp.common.application.IdempotencyService;
import com.company.erp.common.application.IdempotentCommand;
import com.company.erp.common.domain.BusinessException;
import com.company.erp.common.domain.RecordStatus;
import com.company.erp.common.infrastructure.persistence.Specs;
import com.company.erp.masterdata.application.dto.ProductCategoryRequest;
import com.company.erp.masterdata.application.dto.ProductCategoryResponse;
import com.company.erp.masterdata.application.dto.UpdateProductCategoryRequest;
import com.company.erp.masterdata.domain.ProductCategory;
import com.company.erp.masterdata.infrastructure.persistence.ProductCategoryRepository;
import com.company.erp.system.application.CurrentUser;
import com.company.erp.system.application.dto.ChangeStatusRequest;
import java.util.HashSet;
import java.util.Set;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Nhóm hàng dạng cây (BR-MD-03). Danh sách trả phẳng (có {@code parentId}); giao diện dựng cây. */
@Service
public class ProductCategoryService {

    private static final Set<String> SORTABLE = Set.of("code", "name");
    private static final int MAX_DEPTH = 10;

    private final ProductCategoryRepository repository;
    private final MasterDataGuard guard;
    private final IdempotencyService idempotency;

    public ProductCategoryService(ProductCategoryRepository repository, MasterDataGuard guard,
                                  IdempotencyService idempotency) {
        this.repository = repository;
        this.guard = guard;
        this.idempotency = idempotency;
    }

    @Transactional(readOnly = true)
    public Page<ProductCategoryResponse> search(CurrentUser actor, Long companyId, String keyword,
                                                RecordStatus status, Pageable pageable) {
        guard.requireCompany(actor, companyId);
        return repository.findAll(Specs.allOf(
                        Specs.<ProductCategory>equalsIfPresent("companyId", companyId),
                        Specs.keyword(keyword, "code", "name"),
                        Specs.equalsIfPresent("status", status)),
                PageRequests.checked(pageable, SORTABLE, Sort.by("code")))
                .map(ProductCategoryService::toResponse);
    }

    @Transactional
    public ProductCategoryResponse create(CurrentUser actor, String idempotencyKey, ProductCategoryRequest request) {
        IdempotentCommand command = new IdempotentCommand(actor.id(), idempotencyKey,
                "POST /api/v1/product-categories", request);
        return idempotency.execute(command, ProductCategoryResponse.class, () -> {
            guard.requireCompany(actor, request.companyId());
            guard.requireNewCode(repository.existsByCompanyIdAndCode(request.companyId(), request.code()),
                    "Nhóm hàng", request.code());
            requireParent(request.companyId(), null, request.parentId());
            ProductCategory category = repository.saveAndFlush(new ProductCategory(request.companyId(),
                    request.code(), request.name().trim(), request.parentId()));
            ProductCategoryResponse response = toResponse(category);
            guard.audit(actor, MasterDataAudit.CREATE, MasterDataAudit.PRODUCT_CATEGORY, category.getId(), null, null,
                    response, category.getCompanyId());
            return response;
        });
    }

    @Transactional
    public ProductCategoryResponse update(CurrentUser actor, Long id, UpdateProductCategoryRequest request) {
        ProductCategory category = guard.visible(actor, repository.findById(id));
        category.verifyVersion(request.version());
        requireParent(category.getCompanyId(), id, request.parentId());
        ProductCategoryResponse before = toResponse(category);
        category.update(request.name().trim(), request.parentId());
        ProductCategoryResponse after = toResponse(repository.saveAndFlush(category));
        guard.audit(actor, MasterDataAudit.UPDATE, MasterDataAudit.PRODUCT_CATEGORY, id, null, before, after,
                category.getCompanyId());
        return after;
    }

    @Transactional
    public ProductCategoryResponse changeStatus(CurrentUser actor, Long id, ChangeStatusRequest request) {
        ProductCategory category = guard.visible(actor, repository.findById(id));
        category.verifyVersion(request.version());
        ProductCategoryResponse before = toResponse(category);
        category.changeStatus(request.status());
        ProductCategoryResponse after = toResponse(repository.saveAndFlush(category));
        guard.audit(actor, MasterDataAudit.CHANGE_STATUS, MasterDataAudit.PRODUCT_CATEGORY, id, request.reason(),
                before, after, category.getCompanyId());
        return after;
    }

    /** Nhóm cha cùng công ty, đang hoạt động, không tạo vòng (cha không phải chính nó hoặc con cháu của nó). */
    private void requireParent(Long companyId, Long selfId, Long parentId) {
        if (parentId == null) {
            return;
        }
        guard.requireActiveReference(repository.findById(parentId), companyId, "parentId", "Nhóm cha");
        Set<Long> visited = new HashSet<>();
        Long current = parentId;
        while (current != null) {
            if (current.equals(selfId) || !visited.add(current) || visited.size() > MAX_DEPTH) {
                throw BusinessException.invalidField("parentId",
                        "Nhóm cha không hợp lệ (tạo vòng lặp hoặc quá " + MAX_DEPTH + " cấp).");
            }
            current = repository.findById(current).map(ProductCategory::getParentId).orElse(null);
        }
    }

    private static ProductCategoryResponse toResponse(ProductCategory category) {
        return new ProductCategoryResponse(category.getId(), category.getCompanyId(), category.getParentId(),
                category.getCode(), category.getName(), category.getStatus(), category.getVersion(),
                category.getUpdatedAt());
    }
}
