package com.company.erp.finance.application;

import com.company.erp.common.api.PageRequests;
import com.company.erp.common.application.BusinessCalendar;
import com.company.erp.common.application.IdempotencyService;
import com.company.erp.common.application.IdempotentCommand;
import com.company.erp.common.domain.BusinessException;
import com.company.erp.finance.application.dto.CashDocumentRequest;
import com.company.erp.finance.application.dto.CashDocumentResponse;
import com.company.erp.finance.application.dto.ReasonRequest;
import com.company.erp.finance.domain.CashDocument;
import com.company.erp.finance.domain.CashDocumentType;
import com.company.erp.finance.domain.CashPurpose;
import com.company.erp.finance.domain.FinanceDocumentStatus;
import com.company.erp.finance.infrastructure.persistence.CashDocumentRepository;
import com.company.erp.system.application.ApprovalService;
import com.company.erp.system.application.CurrentUser;
import com.company.erp.system.application.OrganizationQueryService.BranchRef;
import jakarta.persistence.criteria.Predicate;
import java.time.LocalDate;
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
 * Lập, sửa, hủy chứng từ thu chi (BR-CSH-02; D-61 không qua duyệt). Ghi sổ, đảo và kiểm soát ngưỡng chi (BR-CSH-03) ở
 * {@link CashDocumentPostingService}.
 */
@Service
public class CashDocumentService {

    static final String VIEW = "cash-document:view";
    static final String CREATE = "cash-document:create";
    private static final Set<String> SORTABLE = Set.of("documentDate", "documentNo", "totalAmount", "createdAt");

    public record CashDocumentFilter(Long companyId, Long branchId, CashDocumentType documentType,
                                     CashPurpose purpose, FinanceDocumentStatus status, Long cashAccountId,
                                     Long partnerId, LocalDate dateFrom, LocalDate dateTo, String keyword) {
    }

    private final CashDocumentRepository repository;
    private final CashDocumentMapper mapper;
    private final CashDocumentContent content;
    private final FinanceAccess access;
    private final ApprovalService approvalService;
    private final IdempotencyService idempotency;
    private final BusinessCalendar calendar;

    public CashDocumentService(CashDocumentRepository repository, CashDocumentMapper mapper,
                               CashDocumentContent content, FinanceAccess access, ApprovalService approvalService,
                               IdempotencyService idempotency, BusinessCalendar calendar) {
        this.repository = repository;
        this.mapper = mapper;
        this.content = content;
        this.access = access;
        this.approvalService = approvalService;
        this.idempotency = idempotency;
        this.calendar = calendar;
    }

    @Transactional(readOnly = true)
    public Page<CashDocumentResponse> search(CurrentUser actor, CashDocumentFilter filter, Pageable pageable) {
        access.requirePermission(actor, VIEW);
        Set<Long> branches = access.branchScope(actor, filter.companyId());
        Specification<CashDocument> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("companyId"), filter.companyId()));
            if (branches != null) {
                predicates.add(root.get("branchId").in(branches));
            }
            if (filter.branchId() != null) {
                predicates.add(cb.equal(root.get("branchId"), filter.branchId()));
            }
            if (filter.documentType() != null) {
                predicates.add(cb.equal(root.get("documentType"), filter.documentType()));
            }
            if (filter.purpose() != null) {
                predicates.add(cb.equal(root.get("purpose"), filter.purpose()));
            }
            if (filter.status() != null) {
                predicates.add(cb.equal(root.get("status"), filter.status()));
            }
            if (filter.cashAccountId() != null) {
                predicates.add(cb.or(cb.equal(root.get("cashAccountId"), filter.cashAccountId()),
                        cb.equal(root.get("targetCashAccountId"), filter.cashAccountId())));
            }
            if (filter.partnerId() != null) {
                predicates.add(cb.equal(root.get("partnerId"), filter.partnerId()));
            }
            if (filter.dateFrom() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("documentDate"), filter.dateFrom()));
            }
            if (filter.dateTo() != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("documentDate"), filter.dateTo()));
            }
            if (filter.keyword() != null && !filter.keyword().isBlank()) {
                String like = "%" + filter.keyword().trim().toLowerCase() + "%";
                predicates.add(cb.or(cb.like(cb.lower(root.get("documentNo")), like),
                        cb.like(cb.lower(root.get("description")), like),
                        cb.like(cb.lower(root.get("counterpartyName")), like)));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
        Page<CashDocument> page = repository.findAll(spec, PageRequests.checked(pageable, SORTABLE,
                Sort.by(Sort.Order.desc("documentDate"), Sort.Order.desc("id"))));
        return mapper.toSummaries(page);
    }

    @Transactional(readOnly = true)
    public CashDocumentResponse get(CurrentUser actor, Long id) {
        access.requirePermission(actor, VIEW);
        return mapper.toResponse(visible(actor, repository.findById(id).orElseThrow(BusinessException::notFound)));
    }

    @Transactional
    public CashDocumentResponse create(CurrentUser actor, String idempotencyKey, CashDocumentRequest request) {
        IdempotentCommand command = new IdempotentCommand(actor.id(), idempotencyKey,
                "POST /api/v1/cash-documents", request);
        return idempotency.execute(command, CashDocumentResponse.class, () -> {
            access.requirePermission(actor, CREATE);
            BranchRef branch = access.usableBranch(actor, request.branchId());
            CashDocument document = new CashDocument(branch.companyId(), branch.id(),
                    access.nextNumber(branch.companyId(), branch.id(), request.documentType().name(),
                            request.documentDate()), request.documentType(), request.purpose());
            content.fill(document, request);
            document = repository.saveAndFlush(document);
            return act(actor, document, "CREATE", null);
        });
    }

    @Transactional
    public CashDocumentResponse update(CurrentUser actor, Long id, CashDocumentRequest request) {
        access.requirePermission(actor, CREATE);
        CashDocument document = lock(actor, id);
        if (request.version() == null) {
            throw BusinessException.invalidField("version", "Thiếu version khi sửa chứng từ.");
        }
        document.verifyVersion(request.version());
        if (!document.getBranchId().equals(request.branchId()) || document.getDocumentType() != request.documentType()
                || document.getPurpose() != request.purpose()) {
            throw BusinessException.invalidField("documentType", "Không đổi chi nhánh, loại, nội dung chứng từ sau "
                    + "khi lập.");
        }
        content.fill(document, request);
        return act(actor, document, "UPDATE", null);
    }

    @Transactional
    public CashDocumentResponse cancel(CurrentUser actor, Long id, String idempotencyKey, ReasonRequest request) {
        IdempotentCommand command = new IdempotentCommand(actor.id(), idempotencyKey,
                "POST /api/v1/cash-documents/" + id + "/cancel", request);
        return idempotency.execute(command, CashDocumentResponse.class, () -> {
            access.requirePermission(actor, CREATE);
            CashDocument document = lock(actor, id);
            // Chứng từ gửi duyệt trước D-61: đóng yêu cầu duyệt còn treo để lịch sử duyệt nhất quán.
            if (document.getStatus() == FinanceDocumentStatus.SUBMITTED) {
                approvalService.cancelPending(actor, document.getDocumentType().name(), id, request.reason());
            }
            document.cancel(request.reason().trim());
            return act(actor, document, "CANCEL", request.reason());
        });
    }

    CashDocument lock(CurrentUser actor, Long id) {
        return visible(actor, repository.findByIdForUpdate(id).orElseThrow(BusinessException::notFound));
    }

    private CashDocument visible(CurrentUser actor, CashDocument document) {
        if (!access.canAccess(actor, document.getCompanyId(), document.getBranchId())) {
            throw BusinessException.notFound();
        }
        return document;
    }

    CashDocumentResponse act(CurrentUser actor, CashDocument document, String action, String reason) {
        repository.saveAndFlush(document);
        access.audit(actor, action, FinanceAccess.ENTITY_CASH_DOCUMENT, document.getId(), document.getDocumentNo(),
                reason, null, document.getStatus(), document.getCompanyId(), document.getBranchId());
        return mapper.toResponse(document);
    }

    LocalDate today() {
        return calendar.today();
    }
}
