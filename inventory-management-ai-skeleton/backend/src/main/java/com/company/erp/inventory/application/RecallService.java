package com.company.erp.inventory.application;

import com.company.erp.common.api.PageRequests;
import com.company.erp.common.application.IdempotencyService;
import com.company.erp.common.application.IdempotentCommand;
import com.company.erp.common.domain.BusinessException;
import com.company.erp.common.domain.ErrorCode;
import com.company.erp.inventory.application.dto.CommentRequest;
import com.company.erp.inventory.application.dto.ReasonRequest;
import com.company.erp.inventory.application.dto.RecallCustomerResponse;
import com.company.erp.inventory.application.dto.RecallRequest;
import com.company.erp.inventory.application.dto.RecallResponse;
import com.company.erp.inventory.domain.InventoryLot;
import com.company.erp.inventory.domain.InventorySerial;
import com.company.erp.inventory.domain.InventoryTransaction;
import com.company.erp.inventory.domain.MovementDirection;
import com.company.erp.inventory.domain.Recall;
import com.company.erp.inventory.domain.RecallStatus;
import com.company.erp.inventory.domain.TransactionType;
import com.company.erp.inventory.infrastructure.persistence.InventoryLotRepository;
import com.company.erp.inventory.infrastructure.persistence.InventorySerialRepository;
import com.company.erp.inventory.infrastructure.persistence.InventoryTransactionRepository;
import com.company.erp.inventory.infrastructure.persistence.RecallRepository;
import com.company.erp.masterdata.application.MasterDataQueryService;
import com.company.erp.masterdata.application.MasterDataQueryService.PartnerInfo;
import com.company.erp.masterdata.application.MasterDataQueryService.ProductInfo;
import com.company.erp.system.application.ApprovalService;
import com.company.erp.system.application.ApprovalSubmission;
import com.company.erp.system.application.CurrentUser;
import jakarta.persistence.criteria.Predicate;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Thu hồi lô / serial (BR-INV-42): duyệt xong thì khóa lô / serial (không xuất bán, BR-INV-23); danh sách khách đã
 * nhận và tiến độ thu hồi tính từ sổ nhật ký kho. Hàng thu hồi về nhập bằng phiếu nhập loại RECALL_IN.
 */
@Service
public class RecallService {

    static final String DOCUMENT_TYPE = "PTH";
    private static final Set<String> SORT_FIELDS = Set.of("recallDate", "documentNo", "createdAt");

    private final RecallRepository recallRepository;
    private final InventoryLotRepository lotRepository;
    private final InventorySerialRepository serialRepository;
    private final InventoryTransactionRepository transactionRepository;
    private final MasterDataQueryService masterData;
    private final InventoryAccess access;
    private final InventoryNumbering numbering;
    private final ApprovalService approvalService;
    private final IdempotencyService idempotency;
    private final Clock clock;

    @SuppressWarnings("java:S107")
    public RecallService(RecallRepository recallRepository, InventoryLotRepository lotRepository,
                         InventorySerialRepository serialRepository,
                         InventoryTransactionRepository transactionRepository, MasterDataQueryService masterData,
                         InventoryAccess access, InventoryNumbering numbering, ApprovalService approvalService,
                         IdempotencyService idempotency, Clock clock) {
        this.recallRepository = recallRepository;
        this.lotRepository = lotRepository;
        this.serialRepository = serialRepository;
        this.transactionRepository = transactionRepository;
        this.masterData = masterData;
        this.access = access;
        this.numbering = numbering;
        this.approvalService = approvalService;
        this.idempotency = idempotency;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public Page<RecallResponse> search(CurrentUser actor, Long companyId, RecallStatus status, Long productId,
                                       Pageable pageable) {
        access.requirePermission(actor, "recall:view");
        if (!actor.dataScope().canAccessCompany(companyId)) {
            throw BusinessException.notFound();
        }
        Specification<Recall> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("companyId"), companyId));
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            if (productId != null) {
                predicates.add(cb.equal(root.get("productId"), productId));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
        Pageable page = PageRequests.checked(pageable, SORT_FIELDS,
                Sort.by(Sort.Order.desc("recallDate"), Sort.Order.desc("id")));
        return recallRepository.findAll(spec, page).map(recall -> toResponse(recall, false));
    }

    @Transactional(readOnly = true)
    public RecallResponse get(CurrentUser actor, Long id) {
        access.requirePermission(actor, "recall:view");
        return toResponse(visible(actor, recallRepository.findById(id).orElseThrow(BusinessException::notFound)),
                true);
    }

    @Transactional
    public RecallResponse create(CurrentUser actor, String idempotencyKey, RecallRequest request) {
        IdempotentCommand command = new IdempotentCommand(actor.id(), idempotencyKey, "POST /api/v1/recalls", request);
        return idempotency.execute(command, RecallResponse.class, () -> {
            access.requirePermission(actor, "recall:create");
            if (!actor.dataScope().canAccessBranch(request.branchId())) {
                throw BusinessException.invalidField("branchId", "Chi nhánh ngoài phạm vi.");
            }
            ProductInfo product = masterData.product(request.productId())
                    .filter(found -> actor.dataScope().canAccessCompany(found.companyId()))
                    .orElseThrow(() -> BusinessException.invalidField("productId", "Hàng hóa không tồn tại."));
            requireTracked(product, request);
            Recall recall = recallRepository.saveAndFlush(new Recall(product.companyId(), request.branchId(),
                    numbering.next(product.companyId(), request.branchId(), DOCUMENT_TYPE, request.recallDate()),
                    request.recallDate(), product.id(), request.lotId(), request.serialId(), request.reason().trim()));
            return act(actor, recall, "CREATE", null);
        });
    }

    @Transactional
    public RecallResponse submit(CurrentUser actor, Long id, String idempotencyKey) {
        IdempotentCommand command = new IdempotentCommand(actor.id(), idempotencyKey,
                "POST /api/v1/recalls/" + id + "/submit", id);
        return idempotency.execute(command, RecallResponse.class, () -> {
            access.requirePermission(actor, "recall:create");
            Recall recall = lock(actor, id);
            Instant now = Instant.now(clock);
            recall.submit(actor.id(), now);
            boolean required = approvalService.submit(actor, new ApprovalSubmission(recall.getCompanyId(),
                    recall.getBranchId(), DOCUMENT_TYPE, recall.getId(), recall.getDocumentNo(), null, null))
                    .approvalRequired();
            if (!required) {
                activate(actor, recall, now);
            }
            return act(actor, recall, "SUBMIT", null);
        });
    }

    @Transactional
    public RecallResponse approve(CurrentUser actor, Long id, String idempotencyKey, CommentRequest request) {
        IdempotentCommand command = new IdempotentCommand(actor.id(), idempotencyKey,
                "POST /api/v1/recalls/" + id + "/approve", request);
        return idempotency.execute(command, RecallResponse.class, () -> {
            access.requirePermission(actor, "recall:approve");
            Recall recall = lock(actor, id);
            if (recall.getStatus() != RecallStatus.SUBMITTED) {
                throw new BusinessException(ErrorCode.INVALID_DOCUMENT_STATE, "Phiếu thu hồi không chờ duyệt.");
            }
            String comment = request == null ? null : request.comment();
            if (approvalService.approve(actor, DOCUMENT_TYPE, id, comment).completed()) {
                activate(actor, recall, Instant.now(clock));
            }
            return act(actor, recall, "APPROVE", comment);
        });
    }

    @Transactional
    public RecallResponse reject(CurrentUser actor, Long id, String idempotencyKey, ReasonRequest request) {
        IdempotentCommand command = new IdempotentCommand(actor.id(), idempotencyKey,
                "POST /api/v1/recalls/" + id + "/reject", request);
        return idempotency.execute(command, RecallResponse.class, () -> {
            access.requirePermission(actor, "recall:approve");
            Recall recall = lock(actor, id);
            if (recall.getStatus() != RecallStatus.SUBMITTED) {
                throw new BusinessException(ErrorCode.INVALID_DOCUMENT_STATE, "Phiếu thu hồi không chờ duyệt.");
            }
            approvalService.reject(actor, DOCUMENT_TYPE, id, request.reason());
            recall.reject();
            return act(actor, recall, "REJECT", request.reason());
        });
    }

    @Transactional
    public RecallResponse cancel(CurrentUser actor, Long id, String idempotencyKey, ReasonRequest request) {
        IdempotentCommand command = new IdempotentCommand(actor.id(), idempotencyKey,
                "POST /api/v1/recalls/" + id + "/cancel", request);
        return idempotency.execute(command, RecallResponse.class, () -> {
            access.requirePermission(actor, "recall:create");
            Recall recall = lock(actor, id);
            if (recall.getStatus() == RecallStatus.SUBMITTED) {
                approvalService.cancelPending(actor, DOCUMENT_TYPE, id, request.reason());
            }
            recall.cancel(request.reason());
            return act(actor, recall, "CANCEL", request.reason());
        });
    }

    /** Đóng đợt thu hồi; lô / serial vẫn bị khóa cho tới khi người có quyền mở khóa (BR-INV-23). */
    @Transactional
    public RecallResponse close(CurrentUser actor, Long id, String idempotencyKey, ReasonRequest request) {
        IdempotentCommand command = new IdempotentCommand(actor.id(), idempotencyKey,
                "POST /api/v1/recalls/" + id + "/close", request);
        return idempotency.execute(command, RecallResponse.class, () -> {
            access.requirePermission(actor, "recall:approve");
            Recall recall = lock(actor, id);
            recall.close(actor.id(), Instant.now(clock));
            return act(actor, recall, "CLOSE", request.reason());
        });
    }

    private void activate(CurrentUser actor, Recall recall, Instant now) {
        recall.activate(actor.id(), now);
        String reason = "Thu hồi " + recall.getDocumentNo();
        if (recall.getLotId() != null) {
            InventoryLot lot = lotRepository.findById(recall.getLotId()).orElseThrow();
            lot.block(reason);
            access.audit(actor, "BLOCK", InventoryAccess.ENTITY_LOT, lot.getId(), lot.getLotNo(), reason, null, null,
                    recall.getCompanyId(), recall.getBranchId());
        }
        if (recall.getSerialId() != null) {
            InventorySerial serial = serialRepository.findById(recall.getSerialId()).orElseThrow();
            serial.block(reason);
            access.audit(actor, "BLOCK", InventoryAccess.ENTITY_SERIAL, serial.getId(), serial.getSerialNo(), reason,
                    null, null, recall.getCompanyId(), recall.getBranchId());
        }
    }

    private void requireTracked(ProductInfo product, RecallRequest request) {
        if (request.lotId() == null && request.serialId() == null) {
            throw BusinessException.invalidField("lotId", "Chọn lô hoặc serial cần thu hồi.");
        }
        if (request.lotId() != null && lotRepository.findById(request.lotId())
                .filter(lot -> lot.getProductId().equals(product.id())).isEmpty()) {
            throw BusinessException.invalidField("lotId", "Lô không thuộc hàng.");
        }
        if (request.serialId() != null && serialRepository.findById(request.serialId())
                .filter(serial -> serial.getProductId().equals(product.id())).isEmpty()) {
            throw BusinessException.invalidField("serialId", "Serial không thuộc hàng.");
        }
    }

    private Recall lock(CurrentUser actor, Long id) {
        return visible(actor, recallRepository.findByIdForUpdate(id).orElseThrow(BusinessException::notFound));
    }

    private static Recall visible(CurrentUser actor, Recall recall) {
        if (!actor.dataScope().canAccessCompany(recall.getCompanyId())) {
            throw BusinessException.notFound();
        }
        return recall;
    }

    private RecallResponse act(CurrentUser actor, Recall recall, String action, String reason) {
        recallRepository.saveAndFlush(recall);
        access.audit(actor, action, InventoryAccess.ENTITY_RECALL, recall.getId(), recall.getDocumentNo(), reason,
                null, recall.getStatus(), recall.getCompanyId(), recall.getBranchId());
        return toResponse(recall, true);
    }

    private RecallResponse toResponse(Recall recall, boolean detailed) {
        ProductInfo product = masterData.product(recall.getProductId()).orElse(null);
        InventoryLot lot = recall.getLotId() == null ? null : lotRepository.findById(recall.getLotId()).orElse(null);
        InventorySerial serial = recall.getSerialId() == null ? null
                : serialRepository.findById(recall.getSerialId()).orElse(null);
        return new RecallResponse(recall.getId(), recall.getCompanyId(), recall.getBranchId(), recall.getDocumentNo(),
                recall.getRecallDate(), recall.getProductId(), product == null ? null : product.code(),
                product == null ? null : product.name(), recall.getLotId(), lot == null ? null : lot.getLotNo(),
                recall.getSerialId(), serial == null ? null : serial.getSerialNo(), recall.getReason(),
                recall.getStatus(), recall.getApprovedAt(), recall.getClosedAt(), recall.getCancelReason(),
                recall.getVersion(), detailed ? customers(recall) : List.of(),
                detailed ? approvalService.history(DOCUMENT_TYPE, recall.getId()) : List.of());
    }

    /** Khách đã nhận lô / serial (xuất có đối tác) và số đã thu hồi về (nhập RECALL_IN), tính từ sổ nhật ký. */
    private List<RecallCustomerResponse> customers(Recall recall) {
        Specification<InventoryTransaction> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("companyId"), recall.getCompanyId()));
            predicates.add(cb.equal(root.get("productId"), recall.getProductId()));
            predicates.add(cb.isNotNull(root.get("partnerId")));
            if (recall.getLotId() != null) {
                predicates.add(cb.equal(root.get("lotId"), recall.getLotId()));
            }
            if (recall.getSerialId() != null) {
                predicates.add(cb.equal(root.get("serialId"), recall.getSerialId()));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
        Map<Long, BigDecimal[]> totals = new TreeMap<>();
        for (InventoryTransaction transaction : transactionRepository.findAll(spec)) {
            BigDecimal[] sums = totals.computeIfAbsent(transaction.getPartnerId(),
                    key -> new BigDecimal[] {BigDecimal.ZERO, BigDecimal.ZERO});
            if (transaction.getTransactionType() == TransactionType.RECALL_IN) {
                sums[1] = sums[1].add(transaction.getQuantity());
            } else if (transaction.getDirection() == MovementDirection.OUT
                    && transaction.getTransactionType() != TransactionType.REVERSAL) {
                sums[0] = sums[0].add(transaction.getQuantity());
            }
        }
        List<RecallCustomerResponse> customers = new ArrayList<>();
        totals.forEach((partnerId, sums) -> {
            PartnerInfo partner = masterData.partner(partnerId).orElse(null);
            customers.add(new RecallCustomerResponse(partnerId, partner == null ? null : partner.code(),
                    partner == null ? null : partner.name(), sums[0], sums[1]));
        });
        return customers;
    }
}
