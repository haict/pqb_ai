package com.company.erp.masterdata.application;

import com.company.erp.common.api.PageRequests;
import com.company.erp.common.application.IdempotencyService;
import com.company.erp.common.application.IdempotentCommand;
import com.company.erp.common.domain.BusinessException;
import com.company.erp.common.domain.ErrorCode;
import com.company.erp.common.domain.RecordStatus;
import com.company.erp.common.infrastructure.persistence.Specs;
import com.company.erp.masterdata.application.dto.ReferenceItemRequest;
import com.company.erp.masterdata.application.dto.ReferenceItemResponse;
import com.company.erp.masterdata.application.dto.UpdateReferenceItemRequest;
import com.company.erp.masterdata.domain.ReferenceCategory;
import com.company.erp.masterdata.domain.ReferenceItem;
import com.company.erp.masterdata.infrastructure.persistence.ReferenceItemRepository;
import com.company.erp.system.application.CurrentUser;
import com.company.erp.system.application.dto.ChangeStatusRequest;
import java.util.Set;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Danh mục phụ theo loại: điều khoản, phương thức, loại hợp đồng, khoản mục, lý do (BR-MD-45, BR-MD-46). */
@Service
public class ReferenceItemService {

    private static final Set<String> SORTABLE = Set.of("category", "code", "name");

    private final ReferenceItemRepository repository;
    private final MasterDataGuard guard;
    private final IdempotencyService idempotency;

    public ReferenceItemService(ReferenceItemRepository repository, MasterDataGuard guard,
                                IdempotencyService idempotency) {
        this.repository = repository;
        this.guard = guard;
        this.idempotency = idempotency;
    }

    @Transactional(readOnly = true)
    public Page<ReferenceItemResponse> search(CurrentUser actor, Long companyId, ReferenceCategory category,
                                              String keyword, RecordStatus status, Pageable pageable) {
        guard.requireCompany(actor, companyId);
        return repository.findAll(Specs.allOf(
                        Specs.<ReferenceItem>equalsIfPresent("companyId", companyId),
                        Specs.equalsIfPresent("category", category),
                        Specs.keyword(keyword, "code", "name"),
                        Specs.equalsIfPresent("status", status)),
                PageRequests.checked(pageable, SORTABLE, Sort.by("category", "code")))
                .map(ReferenceItemService::toResponse);
    }

    @Transactional
    public ReferenceItemResponse create(CurrentUser actor, String idempotencyKey, ReferenceItemRequest request) {
        IdempotentCommand command = new IdempotentCommand(actor.id(), idempotencyKey, "POST /api/v1/reference-items",
                request);
        return idempotency.execute(command, ReferenceItemResponse.class, () -> {
            guard.requireCompany(actor, request.companyId());
            if (repository.existsByCompanyIdAndCategoryAndCode(request.companyId(), request.category(),
                    request.code())) {
                throw new BusinessException(ErrorCode.DUPLICATE_CODE, "Mã '" + request.code() + "' đã có trong loại này.");
            }
            ReferenceItem item = repository.saveAndFlush(new ReferenceItem(request.companyId(), request.category(),
                    request.code(), request.name().trim(), request.numericValue()));
            ReferenceItemResponse response = toResponse(item);
            guard.audit(actor, MasterDataAudit.CREATE, MasterDataAudit.REFERENCE_ITEM, item.getId(), null, null,
                    response, item.getCompanyId());
            return response;
        });
    }

    @Transactional
    public ReferenceItemResponse update(CurrentUser actor, Long id, UpdateReferenceItemRequest request) {
        ReferenceItem item = find(actor, id);
        item.verifyVersion(request.version());
        ReferenceItemResponse before = toResponse(item);
        item.update(request.name().trim(), request.numericValue());
        ReferenceItemResponse after = toResponse(repository.saveAndFlush(item));
        guard.audit(actor, MasterDataAudit.UPDATE, MasterDataAudit.REFERENCE_ITEM, id, null, before, after,
                item.getCompanyId());
        return after;
    }

    @Transactional
    public ReferenceItemResponse changeStatus(CurrentUser actor, Long id, ChangeStatusRequest request) {
        ReferenceItem item = find(actor, id);
        item.verifyVersion(request.version());
        ReferenceItemResponse before = toResponse(item);
        item.changeStatus(request.status());
        ReferenceItemResponse after = toResponse(repository.saveAndFlush(item));
        guard.audit(actor, MasterDataAudit.CHANGE_STATUS, MasterDataAudit.REFERENCE_ITEM, id, request.reason(),
                before, after, item.getCompanyId());
        return after;
    }

    private ReferenceItem find(CurrentUser actor, Long id) {
        ReferenceItem item = repository.findById(id).orElseThrow(BusinessException::notFound);
        guard.requireCompany(actor, item.getCompanyId());
        return item;
    }

    private static ReferenceItemResponse toResponse(ReferenceItem item) {
        return new ReferenceItemResponse(item.getId(), item.getCompanyId(), item.getCategory(), item.getCode(),
                item.getName(), item.getNumericValue(), item.getStatus(), item.getVersion(), item.getUpdatedAt());
    }
}
