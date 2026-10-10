package com.company.erp.inventory.application;

import com.company.erp.common.api.PageRequests;
import com.company.erp.common.application.IdempotencyService;
import com.company.erp.common.application.IdempotentCommand;
import com.company.erp.common.domain.BusinessException;
import com.company.erp.common.domain.ErrorCode;
import com.company.erp.inventory.application.dto.CommentRequest;
import com.company.erp.inventory.application.dto.ReasonRequest;
import com.company.erp.inventory.application.dto.StockIssuePolicyRequest;
import com.company.erp.inventory.application.dto.StockIssuePolicyResponse;
import com.company.erp.inventory.domain.PolicyStatus;
import com.company.erp.inventory.domain.StockIssuePolicy;
import com.company.erp.inventory.infrastructure.persistence.StockIssuePolicyRepository;
import com.company.erp.masterdata.application.MasterDataQueryService;
import com.company.erp.masterdata.application.MasterDataQueryService.WarehouseInfo;
import com.company.erp.system.application.ApprovalService;
import com.company.erp.system.application.ApprovalSubmission;
import com.company.erp.system.application.CurrentUser;
import jakarta.persistence.criteria.Predicate;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Chính sách xuất vượt tồn khả dụng (BR-GEN-31, D-10): chỉ có hiệu lực sau khi duyệt; chứng từ xuất tham chiếu
 * chính sách và dùng dần hạn mức khi ghi sổ. Tồn thực tế vẫn không bao giờ âm.
 */
@Service
public class StockIssuePolicyService {

    static final String DOCUMENT_TYPE = "CSX";
    private static final Set<String> SORT_FIELDS = Set.of("validFrom", "documentNo", "createdAt");

    private final StockIssuePolicyRepository policyRepository;
    private final MasterDataQueryService masterData;
    private final InventoryAccess access;
    private final InventoryNumbering numbering;
    private final ApprovalService approvalService;
    private final IdempotencyService idempotency;
    private final Clock clock;

    public StockIssuePolicyService(StockIssuePolicyRepository policyRepository, MasterDataQueryService masterData,
                                   InventoryAccess access, InventoryNumbering numbering,
                                   ApprovalService approvalService, IdempotencyService idempotency, Clock clock) {
        this.policyRepository = policyRepository;
        this.masterData = masterData;
        this.access = access;
        this.numbering = numbering;
        this.approvalService = approvalService;
        this.idempotency = idempotency;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public Page<StockIssuePolicyResponse> search(CurrentUser actor, Long companyId, Long warehouseId,
                                                 PolicyStatus status, Pageable pageable) {
        access.requirePermission(actor, "stock-policy:view");
        Set<Long> warehouses = access.scopeWarehouseIds(actor, companyId);
        Specification<StockIssuePolicy> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("companyId"), companyId));
            if (warehouses != null) {
                predicates.add(root.get("warehouseId").in(warehouses));
            }
            if (warehouseId != null) {
                predicates.add(cb.equal(root.get("warehouseId"), warehouseId));
            }
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
        Pageable page = PageRequests.checked(pageable, SORT_FIELDS,
                Sort.by(Sort.Order.desc("validFrom"), Sort.Order.desc("id")));
        return policyRepository.findAll(spec, page).map(policy -> toResponse(policy, false));
    }

    @Transactional(readOnly = true)
    public StockIssuePolicyResponse get(CurrentUser actor, Long id) {
        access.requirePermission(actor, "stock-policy:view");
        return toResponse(visible(actor, policyRepository.findById(id).orElseThrow(BusinessException::notFound)),
                true);
    }

    @Transactional
    public StockIssuePolicyResponse create(CurrentUser actor, String idempotencyKey,
                                           StockIssuePolicyRequest request) {
        IdempotentCommand command = new IdempotentCommand(actor.id(), idempotencyKey,
                "POST /api/v1/stock-issue-policies", request);
        return idempotency.execute(command, StockIssuePolicyResponse.class, () -> {
            access.requirePermission(actor, "stock-policy:create");
            WarehouseInfo warehouse = access.usableWarehouse(actor, request.warehouseId(), "warehouseId");
            if (request.productId() != null && masterData.product(request.productId())
                    .filter(product -> product.companyId().equals(warehouse.companyId())).isEmpty()) {
                throw BusinessException.invalidField("productId", "Hàng hóa không tồn tại.");
            }
            StockIssuePolicy policy = policyRepository.saveAndFlush(new StockIssuePolicy(warehouse.companyId(),
                    warehouse.branchId(), numbering.next(warehouse.companyId(), warehouse.branchId(), DOCUMENT_TYPE,
                    request.validFrom()), warehouse.id(), request.productId(), request.maxQuantity(),
                    request.validFrom(), request.validTo(), request.reason().trim()));
            return act(actor, policy, "CREATE", null);
        });
    }

    @Transactional
    public StockIssuePolicyResponse submit(CurrentUser actor, Long id, String idempotencyKey) {
        IdempotentCommand command = new IdempotentCommand(actor.id(), idempotencyKey,
                "POST /api/v1/stock-issue-policies/" + id + "/submit", id);
        return idempotency.execute(command, StockIssuePolicyResponse.class, () -> {
            access.requirePermission(actor, "stock-policy:create");
            StockIssuePolicy policy = lock(actor, id);
            Instant now = Instant.now(clock);
            policy.submit(actor.id(), now);
            boolean required = approvalService.submit(actor, new ApprovalSubmission(policy.getCompanyId(),
                    policy.getBranchId(), DOCUMENT_TYPE, policy.getId(), policy.getDocumentNo(), null, null))
                    .approvalRequired();
            if (!required) {
                policy.approve(actor.id(), now);
            }
            return act(actor, policy, "SUBMIT", null);
        });
    }

    @Transactional
    public StockIssuePolicyResponse approve(CurrentUser actor, Long id, String idempotencyKey,
                                            CommentRequest request) {
        IdempotentCommand command = new IdempotentCommand(actor.id(), idempotencyKey,
                "POST /api/v1/stock-issue-policies/" + id + "/approve", request);
        return idempotency.execute(command, StockIssuePolicyResponse.class, () -> {
            access.requirePermission(actor, "stock-policy:approve");
            StockIssuePolicy policy = lock(actor, id);
            requireSubmitted(policy);
            String comment = request == null ? null : request.comment();
            if (approvalService.approve(actor, DOCUMENT_TYPE, id, comment).completed()) {
                policy.approve(actor.id(), Instant.now(clock));
            }
            return act(actor, policy, "APPROVE", comment);
        });
    }

    @Transactional
    public StockIssuePolicyResponse reject(CurrentUser actor, Long id, String idempotencyKey,
                                           ReasonRequest request) {
        IdempotentCommand command = new IdempotentCommand(actor.id(), idempotencyKey,
                "POST /api/v1/stock-issue-policies/" + id + "/reject", request);
        return idempotency.execute(command, StockIssuePolicyResponse.class, () -> {
            access.requirePermission(actor, "stock-policy:approve");
            StockIssuePolicy policy = lock(actor, id);
            requireSubmitted(policy);
            approvalService.reject(actor, DOCUMENT_TYPE, id, request.reason());
            policy.reject();
            return act(actor, policy, "REJECT", request.reason());
        });
    }

    @Transactional
    public StockIssuePolicyResponse cancel(CurrentUser actor, Long id, String idempotencyKey,
                                           ReasonRequest request) {
        IdempotentCommand command = new IdempotentCommand(actor.id(), idempotencyKey,
                "POST /api/v1/stock-issue-policies/" + id + "/cancel", request);
        return idempotency.execute(command, StockIssuePolicyResponse.class, () -> {
            access.requirePermission(actor, "stock-policy:create");
            StockIssuePolicy policy = lock(actor, id);
            if (policy.getStatus() == PolicyStatus.SUBMITTED) {
                approvalService.cancelPending(actor, DOCUMENT_TYPE, id, request.reason());
            }
            policy.cancel(request.reason());
            return act(actor, policy, "CANCEL", request.reason());
        });
    }

    private static void requireSubmitted(StockIssuePolicy policy) {
        if (policy.getStatus() != PolicyStatus.SUBMITTED) {
            throw new BusinessException(ErrorCode.INVALID_DOCUMENT_STATE, "Chính sách " + policy.getDocumentNo()
                    + " không ở trạng thái chờ duyệt.");
        }
    }

    private StockIssuePolicy lock(CurrentUser actor, Long id) {
        return visible(actor, policyRepository.findByIdForUpdate(id).orElseThrow(BusinessException::notFound));
    }

    private StockIssuePolicy visible(CurrentUser actor, StockIssuePolicy policy) {
        if (!access.canAccessWarehouse(actor, policy.getWarehouseId())) {
            throw BusinessException.notFound();
        }
        return policy;
    }

    private StockIssuePolicyResponse act(CurrentUser actor, StockIssuePolicy policy, String action, String reason) {
        policyRepository.saveAndFlush(policy);
        access.audit(actor, action, InventoryAccess.ENTITY_POLICY, policy.getId(), policy.getDocumentNo(), reason,
                null, policy.getStatus(), policy.getCompanyId(), policy.getBranchId());
        return toResponse(policy, true);
    }

    private StockIssuePolicyResponse toResponse(StockIssuePolicy policy, boolean detailed) {
        return new StockIssuePolicyResponse(policy.getId(), policy.getCompanyId(), policy.getBranchId(),
                policy.getDocumentNo(), policy.getWarehouseId(), policy.getProductId(), policy.getMaxQuantity(),
                policy.getUsedQuantity(), policy.getUsedByDocumentId(), policy.getValidFrom(), policy.getValidTo(), policy.getReason(),
                policy.getStatus(), policy.getApprovedAt(), policy.getCancelReason(), policy.getVersion(),
                detailed ? approvalService.history(DOCUMENT_TYPE, policy.getId()) : List.of());
    }
}
