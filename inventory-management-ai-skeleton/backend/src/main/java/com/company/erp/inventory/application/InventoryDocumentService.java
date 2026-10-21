package com.company.erp.inventory.application;

import com.company.erp.common.api.PageRequests;
import com.company.erp.common.application.BusinessCalendar;
import com.company.erp.common.application.IdempotencyService;
import com.company.erp.common.application.IdempotentCommand;
import com.company.erp.common.domain.BusinessException;
import com.company.erp.common.domain.ErrorCode;
import com.company.erp.inventory.application.dto.InventoryDocumentRequest;
import com.company.erp.inventory.application.dto.InventoryDocumentResponse;
import com.company.erp.inventory.application.dto.InventoryDocumentSummary;
import com.company.erp.inventory.application.dto.ReasonRequest;
import com.company.erp.inventory.application.dto.ReverseRequest;
import com.company.erp.inventory.domain.InventoryDocument;
import com.company.erp.inventory.domain.InventoryDocumentStatus;
import com.company.erp.inventory.domain.InventoryDocumentType;
import com.company.erp.inventory.infrastructure.persistence.InventoryDocumentRepository;
import com.company.erp.system.application.ApprovalService;
import com.company.erp.system.application.CurrentUser;
import jakarta.persistence.criteria.Predicate;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Chứng từ kho: lập, sửa, hủy, lập chứng từ đảo (BR mục 5.1, BR-INV-30..33, BR-GEN-21; D-61 không qua duyệt).
 * Ghi sổ ở {@link InventoryDocumentPostingService}. Quyền theo loại chứng từ kiểm tra tại đây vì một endpoint phục
 * vụ nhiều loại.
 */
@Service
public class InventoryDocumentService {

    private static final Set<String> SORT_FIELDS = Set.of("documentDate", "postingDate", "documentNo", "createdAt",
            "updatedAt");

    private final InventoryDocumentRepository documentRepository;
    private final InventoryDocumentBuilder builder;
    private final InventoryDocumentMapper mapper;
    private final InventoryAccess access;
    private final InventoryNumbering numbering;
    private final ApprovalService approvalService;
    private final IdempotencyService idempotency;
    private final BusinessCalendar calendar;
    private final Clock clock;

    public InventoryDocumentService(InventoryDocumentRepository documentRepository, InventoryDocumentBuilder builder,
                                    InventoryDocumentMapper mapper, InventoryAccess access,
                                    InventoryNumbering numbering, ApprovalService approvalService,
                                    IdempotencyService idempotency, BusinessCalendar calendar, Clock clock) {
        this.documentRepository = documentRepository;
        this.builder = builder;
        this.mapper = mapper;
        this.access = access;
        this.numbering = numbering;
        this.approvalService = approvalService;
        this.idempotency = idempotency;
        this.calendar = calendar;
        this.clock = clock;
    }

    /** Bộ lọc danh sách chứng từ kho. */
    public record SearchFilter(Long companyId, InventoryDocumentType documentType, String subType,
                               InventoryDocumentStatus status, Long warehouseId, String keyword, LocalDate dateFrom,
                               LocalDate dateTo) {
    }

    @Transactional(readOnly = true)
    public Page<InventoryDocumentSummary> search(CurrentUser actor, SearchFilter filter, Pageable pageable) {
        Set<Long> warehouses = access.scopeWarehouseIds(actor, filter.companyId());
        List<InventoryDocumentType> types = Arrays.stream(InventoryDocumentType.values())
                .filter(type -> actor.hasPermission(type.viewPermission()))
                .filter(type -> filter.documentType() == null || type == filter.documentType())
                .toList();
        if (types.isEmpty()) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        Specification<InventoryDocument> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("companyId"), filter.companyId()));
            predicates.add(root.get("documentType").in(types));
            if (warehouses != null) {
                predicates.add(cb.or(root.get("warehouseId").in(warehouses),
                        root.get("destinationWarehouseId").in(warehouses)));
            }
            if (filter.subType() != null && !filter.subType().isBlank()) {
                predicates.add(cb.equal(root.get("subType"), filter.subType().trim()));
            }
            if (filter.status() != null) {
                predicates.add(cb.equal(root.get("status"), filter.status()));
            }
            if (filter.warehouseId() != null) {
                predicates.add(cb.or(cb.equal(root.get("warehouseId"), filter.warehouseId()),
                        cb.equal(root.get("destinationWarehouseId"), filter.warehouseId())));
            }
            if (filter.keyword() != null && !filter.keyword().isBlank()) {
                predicates.add(cb.like(cb.lower(root.get("documentNo")),
                        "%" + filter.keyword().trim().toLowerCase() + "%"));
            }
            if (filter.dateFrom() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("documentDate"), filter.dateFrom()));
            }
            if (filter.dateTo() != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("documentDate"), filter.dateTo()));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
        Pageable page = PageRequests.checked(pageable, SORT_FIELDS,
                Sort.by(Sort.Order.desc("documentDate"), Sort.Order.desc("id")));
        return documentRepository.findAll(spec, page).map(mapper::toSummary);
    }

    @Transactional(readOnly = true)
    public InventoryDocumentResponse get(CurrentUser actor, Long id) {
        InventoryDocument document = documentRepository.findById(id).orElseThrow(BusinessException::notFound);
        requireVisible(actor, document);
        return mapper.toResponse(document, actor);
    }

    @Transactional
    public InventoryDocumentResponse create(CurrentUser actor, String idempotencyKey,
                                            InventoryDocumentRequest request) {
        IdempotentCommand command = new IdempotentCommand(actor.id(), idempotencyKey,
                "POST /api/v1/inventory-documents", request);
        return idempotency.execute(command, InventoryDocumentResponse.class, () -> createDraft(actor, request));
    }

    /** Lập chứng từ nháp trong transaction của người gọi (dùng chung cho lập tay và nhập Excel). */
    InventoryDocumentResponse createDraft(CurrentUser actor, InventoryDocumentRequest request) {
        access.requirePermission(actor, request.documentType().permission("create"));
        InventoryDocumentBuilder.Built built = builder.build(actor, request, null);
        String number = numbering.next(built.companyId(), built.branchId(), request.documentType().name(),
                request.documentDate());
        InventoryDocument document = new InventoryDocument(built.companyId(), built.branchId(),
                request.documentType(), number, built.header());
        document.replaceLines(built.lines());
        documentRepository.saveAndFlush(document);
        InventoryDocumentResponse response = mapper.toResponse(document, actor);
        audit(actor, "CREATE", document, null, null, response);
        return response;
    }

    @Transactional
    public InventoryDocumentResponse update(CurrentUser actor, Long id, InventoryDocumentRequest request) {
        InventoryDocument document = lockVisible(actor, id);
        access.requirePermission(actor, document.getDocumentType().permission("create"));
        if (request.version() == null) {
            throw BusinessException.invalidField("version", "Phải gửi version khi sửa.");
        }
        document.verifyVersion(request.version());
        if (request.documentType() != document.getDocumentType() || document.getStockCountId() != null
                || document.getSourceType() != null) {
            throw BusinessException.invalidField("documentType", "Không đổi được loại chứng từ, hoặc chứng từ do hệ "
                    + "thống sinh không sửa được.");
        }
        InventoryDocumentResponse before = mapper.toResponse(document, actor);
        InventoryDocumentBuilder.Built built = builder.build(actor, request, document.getId());
        if (!built.branchId().equals(document.getBranchId())) {
            throw BusinessException.invalidField("warehouseId", "Kho phải thuộc chi nhánh của chứng từ.");
        }
        document.clearLines();
        documentRepository.saveAndFlush(document);
        document.edit(built.header(), built.lines());
        documentRepository.saveAndFlush(document);
        InventoryDocumentResponse response = mapper.toResponse(document, actor);
        audit(actor, "UPDATE", document, null, before, response);
        return response;
    }

    @Transactional
    public InventoryDocumentResponse cancel(CurrentUser actor, Long id, String idempotencyKey,
                                            ReasonRequest request) {
        IdempotentCommand command = new IdempotentCommand(actor.id(), idempotencyKey,
                "POST /api/v1/inventory-documents/" + id + "/cancel", request);
        return idempotency.execute(command, InventoryDocumentResponse.class, () -> {
            InventoryDocument document = lockVisible(actor, id);
            access.requirePermission(actor, document.getDocumentType().permission("create"));
            if (document.getStockCountId() != null) {
                throw new BusinessException(ErrorCode.INVALID_DOCUMENT_STATE,
                        "Chứng từ sinh từ phiếu kiểm kê; hủy phiếu kiểm kê thay vì hủy chứng từ này.");
            }
            // Chứng từ gửi duyệt trước D-61: đóng yêu cầu duyệt còn treo để lịch sử duyệt nhất quán.
            if (document.getStatus() == InventoryDocumentStatus.SUBMITTED) {
                approvalService.cancelPending(actor, document.getDocumentType().name(), id, request.reason());
            }
            document.cancel(actor.id(), Instant.now(clock), request.reason());
            if (document.getReversalOfId() != null) {
                documentRepository.findByIdForUpdate(document.getReversalOfId())
                        .ifPresent(InventoryDocument::unlinkReversal);
            }
            return act(actor, document, "CANCEL", request.reason());
        });
    }

    /**
     * Lập chứng từ đảo (nháp) cho chứng từ đã ghi sổ (BR-GEN-03, BR-GEN-21; D-61 không qua duyệt). Chứng từ gốc chỉ
     * chuyển REVERSED khi chứng từ đảo được ghi sổ.
     */
    @Transactional
    public InventoryDocumentResponse reverse(CurrentUser actor, Long id, String idempotencyKey,
                                             ReverseRequest request) {
        IdempotentCommand command = new IdempotentCommand(actor.id(), idempotencyKey,
                "POST /api/v1/inventory-documents/" + id + "/reverse", request);
        return idempotency.execute(command, InventoryDocumentResponse.class, () -> {
            access.requirePermission(actor, InventoryDocumentType.PDK.permission("create"));
            InventoryDocument original = lockVisible(actor, id);
            if (original.getDocumentType() == InventoryDocumentType.PDK) {
                throw new BusinessException(ErrorCode.INVALID_DOCUMENT_STATE, "Không đảo chứng từ đảo.");
            }
            original.requireReversible();
            if (original.getSourceType() != null) {
                throw new BusinessException(ErrorCode.INVALID_DOCUMENT_STATE, "Chứng từ " + original.getDocumentNo()
                        + " sinh từ " + original.getSourceNo() + "; đảo chứng từ nguồn để cập nhật chỉ tiêu nguồn.");
            }
            LocalDate postingDate = request.postingDate() != null ? request.postingDate() : calendar.today();
            InventoryDocument reversal = new InventoryDocument(original.getCompanyId(), original.getBranchId(),
                    InventoryDocumentType.PDK, numbering.next(original.getCompanyId(), original.getBranchId(),
                    InventoryDocumentType.PDK.name(), postingDate),
                    new InventoryDocument.Header(original.getDocumentType().name(), postingDate, postingDate,
                            original.getWarehouseId(), original.getDestinationWarehouseId(),
                            original.getTransitWarehouseId(), original.getPartnerId(), null, request.reason().trim(),
                            "Đảo " + original.getDocumentNo(), null, null, null, original.getId()));
            documentRepository.saveAndFlush(reversal);
            original.linkReversal(reversal.getId());
            audit(actor, "REVERSE_REQUEST", original, request.reason(), null, null);
            return act(actor, reversal, "CREATE", request.reason());
        });
    }

    private InventoryDocumentResponse act(CurrentUser actor, InventoryDocument document, String action,
                                          String reason) {
        documentRepository.saveAndFlush(document);
        InventoryDocumentResponse response = mapper.toResponse(document, actor);
        audit(actor, action, document, reason, null, response.status());
        return response;
    }

    InventoryDocument lockVisible(CurrentUser actor, Long id) {
        InventoryDocument document = documentRepository.findByIdForUpdate(id).orElseThrow(BusinessException::notFound);
        requireVisible(actor, document);
        return document;
    }

    void requireVisible(CurrentUser actor, InventoryDocument document) {
        boolean inScope = access.canAccessWarehouse(actor, document.getWarehouseId())
                || access.canAccessWarehouse(actor, document.getDestinationWarehouseId());
        if (!inScope || !actor.dataScope().canAccessCompany(document.getCompanyId())) {
            throw BusinessException.notFound();
        }
        access.requirePermission(actor, document.getDocumentType().viewPermission());
    }

    void audit(CurrentUser actor, String action, InventoryDocument document, String reason, Object before,
               Object after) {
        access.audit(actor, action, InventoryAccess.ENTITY_DOCUMENT, document.getId(), document.getDocumentNo(),
                reason, before, after, document.getCompanyId(), document.getBranchId());
    }
}
