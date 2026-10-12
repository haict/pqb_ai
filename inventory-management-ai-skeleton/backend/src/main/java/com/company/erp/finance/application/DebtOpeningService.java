package com.company.erp.finance.application;

import com.company.erp.common.api.PageRequests;
import com.company.erp.common.application.BusinessCalendar;
import com.company.erp.common.application.IdempotencyService;
import com.company.erp.common.application.IdempotentCommand;
import com.company.erp.common.domain.BusinessException;
import com.company.erp.common.domain.ErrorCode;
import com.company.erp.finance.application.dto.CommentRequest;
import com.company.erp.finance.application.dto.DebtOpeningLineRequest;
import com.company.erp.finance.application.dto.DebtOpeningRequest;
import com.company.erp.finance.application.dto.DebtOpeningResponse;
import com.company.erp.finance.application.dto.ReasonRequest;
import com.company.erp.finance.domain.DebtOpeningDocument;
import com.company.erp.finance.domain.DebtOpeningLine;
import com.company.erp.finance.domain.DebtType;
import com.company.erp.finance.domain.FinanceDocumentStatus;
import com.company.erp.finance.infrastructure.persistence.DebtItemRepository;
import com.company.erp.finance.infrastructure.persistence.DebtOpeningDocumentRepository;
import com.company.erp.masterdata.application.MasterDataQueryService.PartnerInfo;
import com.company.erp.system.application.ApprovalService;
import com.company.erp.system.application.ApprovalSubmission;
import com.company.erp.system.application.CurrentUser;
import com.company.erp.system.application.OrganizationQueryService;
import com.company.erp.system.application.OrganizationQueryService.BranchRef;
import jakarta.persistence.criteria.Predicate;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Lập, sửa, gửi duyệt, duyệt, từ chối, hủy chứng từ công nợ đầu kỳ (BR-MIG-01, BR-AR-02). Ghi sổ, đảo ở
 * {@link DebtOpeningPostingService}.
 */
@Service
public class DebtOpeningService {

    static final String DOCUMENT_TYPE = "CNDK";
    static final String VIEW = "debt-opening:view";
    static final String CREATE = "debt-opening:create";
    static final String APPROVE = "debt-opening:approve";
    private static final Set<String> SORTABLE = Set.of("documentDate", "documentNo", "createdAt");

    public record DebtOpeningFilter(Long companyId, Long branchId, DebtType debtType, FinanceDocumentStatus status,
                                    String keyword) {
    }

    private final DebtOpeningDocumentRepository repository;
    private final DebtItemRepository itemRepository;
    private final DebtOpeningMapper mapper;
    private final FinanceAccess access;
    private final OrganizationQueryService organization;
    private final ApprovalService approvalService;
    private final IdempotencyService idempotency;
    private final BusinessCalendar calendar;
    private final Clock clock;

    @SuppressWarnings("java:S107")
    public DebtOpeningService(DebtOpeningDocumentRepository repository, DebtItemRepository itemRepository,
                              DebtOpeningMapper mapper, FinanceAccess access, OrganizationQueryService organization,
                              ApprovalService approvalService, IdempotencyService idempotency,
                              BusinessCalendar calendar, Clock clock) {
        this.repository = repository;
        this.itemRepository = itemRepository;
        this.mapper = mapper;
        this.access = access;
        this.organization = organization;
        this.approvalService = approvalService;
        this.idempotency = idempotency;
        this.calendar = calendar;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public Page<DebtOpeningResponse> search(CurrentUser actor, DebtOpeningFilter filter, Pageable pageable) {
        access.requirePermission(actor, VIEW);
        Set<Long> branches = access.branchScope(actor, filter.companyId());
        Specification<DebtOpeningDocument> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("companyId"), filter.companyId()));
            if (branches != null) {
                predicates.add(root.get("branchId").in(branches));
            }
            if (filter.branchId() != null) {
                predicates.add(cb.equal(root.get("branchId"), filter.branchId()));
            }
            if (filter.debtType() != null) {
                predicates.add(cb.equal(root.get("debtType"), filter.debtType()));
            }
            if (filter.status() != null) {
                predicates.add(cb.equal(root.get("status"), filter.status()));
            }
            if (filter.keyword() != null && !filter.keyword().isBlank()) {
                String like = "%" + filter.keyword().trim().toLowerCase() + "%";
                predicates.add(cb.or(cb.like(cb.lower(root.get("documentNo")), like),
                        cb.like(cb.lower(root.get("description")), like)));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
        return repository.findAll(spec, PageRequests.checked(pageable, SORTABLE,
                        Sort.by(Sort.Order.desc("documentDate"), Sort.Order.desc("id"))))
                .map(document -> mapper.toResponse(document, false));
    }

    @Transactional(readOnly = true)
    public DebtOpeningResponse get(CurrentUser actor, Long id) {
        access.requirePermission(actor, VIEW);
        return mapper.toResponse(visible(actor, repository.findById(id).orElseThrow(BusinessException::notFound)),
                true);
    }

    @Transactional
    public DebtOpeningResponse create(CurrentUser actor, String idempotencyKey, DebtOpeningRequest request) {
        IdempotentCommand command = new IdempotentCommand(actor.id(), idempotencyKey,
                "POST /api/v1/debt-openings", request);
        return idempotency.execute(command, DebtOpeningResponse.class, () -> {
            access.requirePermission(actor, CREATE);
            BranchRef branch = access.usableBranch(actor, request.branchId());
            DebtOpeningDocument document = new DebtOpeningDocument(branch.companyId(), branch.id(),
                    access.nextNumber(branch.companyId(), branch.id(), DOCUMENT_TYPE, request.documentDate()),
                    request.debtType());
            fill(document, request);
            document = repository.saveAndFlush(document);
            return act(actor, document, "CREATE", null);
        });
    }

    @Transactional
    public DebtOpeningResponse update(CurrentUser actor, Long id, DebtOpeningRequest request) {
        access.requirePermission(actor, CREATE);
        DebtOpeningDocument document = lock(actor, id);
        if (request.version() == null) {
            throw BusinessException.invalidField("version", "Thiếu version khi sửa chứng từ.");
        }
        document.verifyVersion(request.version());
        if (!document.getBranchId().equals(request.branchId()) || document.getDebtType() != request.debtType()) {
            throw BusinessException.invalidField("branchId", "Không đổi chi nhánh, loại công nợ sau khi lập.");
        }
        fill(document, request);
        return act(actor, document, "UPDATE", null);
    }

    @Transactional
    public DebtOpeningResponse submit(CurrentUser actor, Long id, String idempotencyKey) {
        IdempotentCommand command = new IdempotentCommand(actor.id(), idempotencyKey,
                "POST /api/v1/debt-openings/" + id + "/submit", id);
        return idempotency.execute(command, DebtOpeningResponse.class, () -> {
            access.requirePermission(actor, CREATE);
            DebtOpeningDocument document = lock(actor, id);
            Instant now = Instant.now(clock);
            document.submit(actor.id(), now);
            boolean required = approvalService.submit(actor, new ApprovalSubmission(document.getCompanyId(),
                    document.getBranchId(), DOCUMENT_TYPE, document.getId(), document.getDocumentNo(),
                    document.getTotalAmount(), null)).approvalRequired();
            if (!required) {
                document.approve(actor.id(), now);
            }
            return act(actor, document, "SUBMIT", null);
        });
    }

    @Transactional
    public DebtOpeningResponse approve(CurrentUser actor, Long id, String idempotencyKey, CommentRequest request) {
        IdempotentCommand command = new IdempotentCommand(actor.id(), idempotencyKey,
                "POST /api/v1/debt-openings/" + id + "/approve", request);
        return idempotency.execute(command, DebtOpeningResponse.class, () -> {
            access.requirePermission(actor, APPROVE);
            DebtOpeningDocument document = lock(actor, id);
            requireSubmitted(document);
            String comment = request == null ? null : request.comment();
            if (approvalService.approve(actor, DOCUMENT_TYPE, id, comment).completed()) {
                document.approve(actor.id(), Instant.now(clock));
            }
            return act(actor, document, "APPROVE", comment);
        });
    }

    @Transactional
    public DebtOpeningResponse reject(CurrentUser actor, Long id, String idempotencyKey, ReasonRequest request) {
        IdempotentCommand command = new IdempotentCommand(actor.id(), idempotencyKey,
                "POST /api/v1/debt-openings/" + id + "/reject", request);
        return idempotency.execute(command, DebtOpeningResponse.class, () -> {
            access.requirePermission(actor, APPROVE);
            DebtOpeningDocument document = lock(actor, id);
            requireSubmitted(document);
            approvalService.reject(actor, DOCUMENT_TYPE, id, request.reason());
            document.reject(request.reason().trim());
            return act(actor, document, "REJECT", request.reason());
        });
    }

    @Transactional
    public DebtOpeningResponse cancel(CurrentUser actor, Long id, String idempotencyKey, ReasonRequest request) {
        IdempotentCommand command = new IdempotentCommand(actor.id(), idempotencyKey,
                "POST /api/v1/debt-openings/" + id + "/cancel", request);
        return idempotency.execute(command, DebtOpeningResponse.class, () -> {
            access.requirePermission(actor, CREATE);
            DebtOpeningDocument document = lock(actor, id);
            if (document.getStatus() == FinanceDocumentStatus.SUBMITTED) {
                approvalService.cancelPending(actor, DOCUMENT_TYPE, id, request.reason());
            }
            document.cancel(request.reason().trim());
            return act(actor, document, "CANCEL", request.reason());
        });
    }

    /** Kiểm tra và thay nội dung chứng từ: đối tượng, hóa đơn không trùng, hạn thanh toán (BR-AR-02). */
    private void fill(DebtOpeningDocument document, DebtOpeningRequest request) {
        List<DebtOpeningLine> lines = new ArrayList<>();
        Set<String> invoices = new HashSet<>();
        for (int i = 0; i < request.lines().size(); i++) {
            DebtOpeningLineRequest line = request.lines().get(i);
            String field = "lines[" + i + "]";
            PartnerInfo partner = access.debtPartner(document.getCompanyId(), line.partnerId(),
                    document.getDebtType(), field + ".partnerId");
            String invoiceNo = line.invoiceNo().trim();
            if (!invoices.add(partner.id() + "|" + invoiceNo)) {
                throw BusinessException.invalidField(field + ".invoiceNo", "Hóa đơn " + invoiceNo
                        + " của " + partner.code() + " bị trùng trong chứng từ.");
            }
            if (itemRepository.existsActiveInvoice(document.getCompanyId(), document.getDebtType(), partner.id(),
                    invoiceNo)) {
                throw BusinessException.invalidField(field + ".invoiceNo", "Hóa đơn " + invoiceNo + " của "
                        + partner.code() + " đã có công nợ.");
            }
            requireSalesUser(document.getCompanyId(), line.salesUserId(), field);
            LocalDate dueDate = line.dueDate() != null ? line.dueDate()
                    : line.invoiceDate().plusDays(termDays(partner, document.getDebtType()));
            if (dueDate.isBefore(line.invoiceDate())) {
                throw BusinessException.invalidField(field + ".dueDate", "Hạn thanh toán không được trước ngày hóa đơn.");
            }
            lines.add(new DebtOpeningLine(i + 1, partner.id(), invoiceNo, line.invoiceDate(), dueDate,
                    line.amount(), line.salesUserId(), trim(line.note())));
        }
        LocalDate postingDate = request.postingDate() != null ? request.postingDate() : request.documentDate();
        document.replaceContent(request.documentDate(), postingDate, trim(request.description()), lines);
    }

    private void requireSalesUser(Long companyId, Long userId, String field) {
        if (userId != null && organization.user(userId)
                .filter(user -> user.companyId().equals(companyId) && user.active()).isEmpty()) {
            throw BusinessException.invalidField(field + ".salesUserId", "Nhân viên không tồn tại hoặc đã ngừng.");
        }
    }

    private static int termDays(PartnerInfo partner, DebtType debtType) {
        Integer days = switch (debtType) {
            case RECEIVABLE -> partner.paymentTermDays();
            case PAYABLE -> partner.supplierPaymentTermDays();
            case ADVANCE -> 0;
        };
        return days == null ? 0 : days;
    }

    private static void requireSubmitted(DebtOpeningDocument document) {
        if (document.getStatus() != FinanceDocumentStatus.SUBMITTED) {
            throw new BusinessException(ErrorCode.INVALID_DOCUMENT_STATE, "Chứng từ " + document.getDocumentNo()
                    + " không ở trạng thái chờ duyệt.");
        }
    }

    DebtOpeningDocument lock(CurrentUser actor, Long id) {
        return visible(actor, repository.findByIdForUpdate(id).orElseThrow(BusinessException::notFound));
    }

    private DebtOpeningDocument visible(CurrentUser actor, DebtOpeningDocument document) {
        if (!access.canAccess(actor, document.getCompanyId(), document.getBranchId())) {
            throw BusinessException.notFound();
        }
        return document;
    }

    DebtOpeningResponse act(CurrentUser actor, DebtOpeningDocument document, String action, String reason) {
        repository.saveAndFlush(document);
        access.audit(actor, action, FinanceAccess.ENTITY_DEBT_OPENING, document.getId(), document.getDocumentNo(),
                reason, null, document.getStatus(), document.getCompanyId(), document.getBranchId());
        return mapper.toResponse(document, true);
    }

    LocalDate today() {
        return calendar.today();
    }

    private static String trim(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
