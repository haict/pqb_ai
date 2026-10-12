package com.company.erp.finance.application;

import com.company.erp.common.api.PageRequests;
import com.company.erp.common.application.IdempotencyService;
import com.company.erp.common.application.IdempotentCommand;
import com.company.erp.common.domain.BusinessException;
import com.company.erp.common.domain.ErrorCode;
import com.company.erp.finance.application.dto.ConfirmReconciliationRequest;
import com.company.erp.finance.application.dto.DebtItemResponse;
import com.company.erp.finance.application.dto.DebtLedgerLineResponse;
import com.company.erp.finance.application.dto.DebtReconciliationRequest;
import com.company.erp.finance.application.dto.DebtReconciliationResponse;
import com.company.erp.finance.application.dto.DebtStatementResponse;
import com.company.erp.finance.application.dto.ReasonRequest;
import com.company.erp.finance.domain.DebtItem;
import com.company.erp.finance.domain.DebtItemStatus;
import com.company.erp.finance.domain.DebtLedgerEntry;
import com.company.erp.finance.domain.DebtPrepayment;
import com.company.erp.finance.domain.DebtReconciliation;
import com.company.erp.finance.domain.DebtType;
import com.company.erp.finance.domain.ReconciliationStatus;
import com.company.erp.finance.infrastructure.persistence.DebtItemRepository;
import com.company.erp.finance.infrastructure.persistence.DebtLedgerEntryRepository;
import com.company.erp.finance.infrastructure.persistence.DebtPrepaymentRepository;
import com.company.erp.finance.infrastructure.persistence.DebtReconciliationRepository;
import com.company.erp.masterdata.application.MasterDataQueryService.PartnerInfo;
import com.company.erp.system.application.CurrentUser;
import com.company.erp.system.application.OrganizationQueryService.BranchRef;
import jakarta.persistence.criteria.Predicate;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Đối chiếu công nợ (BR-AR-06): bảng đối chiếu một đối tượng theo kỳ (số dư đầu, phát sinh, số dư cuối, hóa đơn còn nợ,
 * trả trước) tính từ sổ chi tiết công nợ; biên bản đối chiếu chốt số dư hệ thống và ghi nhận số đối tượng xác nhận.
 * Chênh lệch xử lý bằng điều chỉnh công nợ có duyệt (BR-AR-07).
 */
@Service
public class DebtReconciliationService {

    static final String VIEW = "debt-reconciliation:view";
    static final String MANAGE = "debt-reconciliation:manage";
    static final String ENTITY = "DEBT_RECONCILIATION";
    private static final Set<String> SORTABLE = Set.of("asOfDate", "documentNo", "createdAt");

    public record ReconciliationFilter(Long companyId, DebtType debtType, Long partnerId,
                                       ReconciliationStatus status) {
    }

    private final DebtReconciliationRepository repository;
    private final DebtLedgerEntryRepository ledgerRepository;
    private final DebtItemRepository itemRepository;
    private final DebtPrepaymentRepository prepaymentRepository;
    private final FinanceAccess access;
    private final IdempotencyService idempotency;
    private final Clock clock;

    @SuppressWarnings("java:S107")
    public DebtReconciliationService(DebtReconciliationRepository repository,
                                     DebtLedgerEntryRepository ledgerRepository, DebtItemRepository itemRepository,
                                     DebtPrepaymentRepository prepaymentRepository, FinanceAccess access,
                                     IdempotencyService idempotency, Clock clock) {
        this.repository = repository;
        this.ledgerRepository = ledgerRepository;
        this.itemRepository = itemRepository;
        this.prepaymentRepository = prepaymentRepository;
        this.access = access;
        this.idempotency = idempotency;
        this.clock = clock;
    }

    /**
     * Bảng đối chiếu: {@code branchId} trống = mọi chi nhánh trong phạm vi người xem. Số dư theo chiều tự nhiên (phải
     * thu, tạm ứng: Nợ − Có; phải trả: Có − Nợ). Hóa đơn còn nợ, trả trước lấy theo hiện tại khi {@code dateTo} là
     * hôm nay trở đi; với ngày quá khứ chỉ trả số dư sổ.
     */
    @Transactional(readOnly = true)
    public DebtStatementResponse statement(CurrentUser actor, Long companyId, Long branchId, DebtType debtType,
                                           Long partnerId, LocalDate dateFrom, LocalDate dateTo) {
        access.requirePermission(actor, VIEW);
        if (dateTo.isBefore(dateFrom)) {
            throw BusinessException.invalidField("dateTo", "Đến ngày phải từ ngày bắt đầu trở đi.");
        }
        Collection<Long> branches = branches(actor, companyId, branchId);
        PartnerInfo partner = partner(companyId, partnerId);
        boolean all = branches == null;
        Collection<Long> branchIds = all ? Set.of(-1L) : branches;
        BigDecimal sign = BigDecimal.valueOf(debtType.increasesOnDebit() ? 1 : -1);
        BigDecimal balance = ledgerRepository.netBefore(companyId, debtType, partnerId, dateFrom, all, branchIds)
                .multiply(sign);
        BigDecimal opening = balance;
        BigDecimal debit = BigDecimal.ZERO;
        BigDecimal credit = BigDecimal.ZERO;
        List<DebtLedgerLineResponse> lines = new ArrayList<>();
        for (DebtLedgerEntry entry : ledgerRepository.findInRange(companyId, debtType, partnerId, dateFrom, dateTo,
                all, branchIds)) {
            debit = debit.add(entry.getDebitAmount());
            credit = credit.add(entry.getCreditAmount());
            balance = balance.add(entry.getDebitAmount().subtract(entry.getCreditAmount()).multiply(sign));
            lines.add(new DebtLedgerLineResponse(entry.getId(), entry.getPostingDate(), entry.getEntryType(),
                    entry.getReferenceType(), entry.getReferenceId(), entry.getReferenceNo(), entry.getDebtItemId(),
                    entry.getDescription(), entry.getDebitAmount(), entry.getCreditAmount(), balance));
        }
        boolean current = !dateTo.isBefore(access.today());
        List<DebtItemResponse> openItems = current ? openItems(companyId, debtType, partner, branches) : List.of();
        BigDecimal openAmount = openItems.stream().map(DebtItemResponse::remainingAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal prepayment = current ? prepaymentRepository.findByCompanyIdAndDebtTypeAndPartnerId(companyId,
                        debtType, partnerId).stream().filter(row -> all || branches.contains(row.getBranchId()))
                .map(DebtPrepayment::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add) : BigDecimal.ZERO;
        return new DebtStatementResponse(debtType, partnerId, partner.code(), partner.name(), branchId, dateFrom,
                dateTo, opening, debit, credit, balance, openAmount, prepayment, lines, openItems);
    }

    @Transactional(readOnly = true)
    public Page<DebtReconciliationResponse> search(CurrentUser actor, ReconciliationFilter filter,
                                                   Pageable pageable) {
        access.requirePermission(actor, VIEW);
        Set<Long> branches = access.branchScope(actor, filter.companyId());
        Specification<DebtReconciliation> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("companyId"), filter.companyId()));
            if (branches != null) {
                predicates.add(root.get("branchId").in(branches));
            }
            if (filter.debtType() != null) {
                predicates.add(cb.equal(root.get("debtType"), filter.debtType()));
            }
            if (filter.partnerId() != null) {
                predicates.add(cb.equal(root.get("partnerId"), filter.partnerId()));
            }
            if (filter.status() != null) {
                predicates.add(cb.equal(root.get("status"), filter.status()));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
        Page<DebtReconciliation> page = repository.findAll(spec, PageRequests.checked(pageable, SORTABLE,
                Sort.by(Sort.Order.desc("asOfDate"), Sort.Order.desc("id"))));
        Map<Long, PartnerInfo> partners = access.partners(page.getContent().stream()
                .map(DebtReconciliation::getPartnerId).toList());
        return page.map(item -> toResponse(item, partners.get(item.getPartnerId())));
    }

    @Transactional(readOnly = true)
    public DebtReconciliationResponse get(CurrentUser actor, Long id) {
        access.requirePermission(actor, VIEW);
        DebtReconciliation reconciliation = visible(actor, repository.findById(id)
                .orElseThrow(BusinessException::notFound));
        return toResponse(reconciliation, access.partners(List.of(reconciliation.getPartnerId()))
                .get(reconciliation.getPartnerId()));
    }

    /** Lập biên bản: chốt số dư hệ thống đến hết ngày đối chiếu từ sổ chi tiết công nợ. */
    @Transactional
    public DebtReconciliationResponse create(CurrentUser actor, String key, DebtReconciliationRequest request) {
        return idempotency.execute(new IdempotentCommand(actor.id(), key, "POST /api/v1/debt-reconciliations",
                request), DebtReconciliationResponse.class, () -> {
                    access.requirePermission(actor, MANAGE);
                    Collection<Long> branches = branches(actor, request.companyId(), request.branchId());
                    PartnerInfo partner = access.debtPartner(request.companyId(), request.partnerId(),
                            request.debtType(), "partnerId");
                    boolean all = branches == null;
                    BigDecimal balance = ledgerRepository.netBefore(request.companyId(), request.debtType(),
                                    partner.id(), request.asOfDate().plusDays(1), all, all ? Set.of(-1L) : branches)
                            .multiply(BigDecimal.valueOf(request.debtType().increasesOnDebit() ? 1 : -1));
                    // Biên bản toàn công ty lấy số theo chi nhánh mặc định của người lập.
                    Long numberBranch = request.branchId() != null ? request.branchId() : actor.defaultBranchId();
                    DebtReconciliation reconciliation = repository.saveAndFlush(new DebtReconciliation(
                            new DebtReconciliation.Snapshot(request.companyId(), request.branchId(),
                                    access.nextNumber(request.companyId(), numberBranch,
                                            DebtReconciliation.TYPE_CODE, request.asOfDate()),
                                    request.debtType(), partner.id(), request.periodFrom(), request.asOfDate(),
                                    balance, trim(request.note()))));
                    audit(actor, "CREATE", reconciliation, null);
                    return toResponse(reconciliation, partner);
                });
    }

    /** Ghi nhận số đối tượng xác nhận: khớp (MATCHED) hoặc lệch (DIFFERENT, xử lý bằng điều chỉnh công nợ). */
    @Transactional
    public DebtReconciliationResponse confirm(CurrentUser actor, Long id, String key,
                                              ConfirmReconciliationRequest request) {
        return idempotency.execute(new IdempotentCommand(actor.id(), key, "POST /api/v1/debt-reconciliations/" + id
                + "/confirm", request), DebtReconciliationResponse.class, () -> {
                    access.requirePermission(actor, MANAGE);
                    DebtReconciliation reconciliation = lock(actor, id);
                    reconciliation.confirm(request.confirmedBalance(), trim(request.contactName()),
                            trim(request.note()), actor.id(), Instant.now(clock));
                    repository.saveAndFlush(reconciliation);
                    audit(actor, "CONFIRM", reconciliation, request.note());
                    return toResponse(reconciliation, partner(reconciliation.getCompanyId(),
                            reconciliation.getPartnerId()));
                });
    }

    @Transactional
    public DebtReconciliationResponse cancel(CurrentUser actor, Long id, String key, ReasonRequest request) {
        return idempotency.execute(new IdempotentCommand(actor.id(), key, "POST /api/v1/debt-reconciliations/" + id
                + "/cancel", request), DebtReconciliationResponse.class, () -> {
                    access.requirePermission(actor, MANAGE);
                    DebtReconciliation reconciliation = lock(actor, id);
                    reconciliation.cancel(request.reason().trim());
                    repository.saveAndFlush(reconciliation);
                    audit(actor, "CANCEL", reconciliation, request.reason());
                    return toResponse(reconciliation, partner(reconciliation.getCompanyId(),
                            reconciliation.getPartnerId()));
                });
    }

    /**
     * Chi nhánh tính số dư: chỉ định thì phải trong phạm vi; để trống = toàn công ty, chỉ người có phạm vi toàn công ty
     * (biên bản không được chốt số dư thiếu chi nhánh ngoài phạm vi). Trả {@code null} = mọi chi nhánh.
     */
    private Collection<Long> branches(CurrentUser actor, Long companyId, Long branchId) {
        Set<Long> scope = access.branchScope(actor, companyId);
        if (branchId != null) {
            BranchRef branch = access.usableBranch(actor, branchId);
            if (!branch.companyId().equals(companyId)) {
                throw BusinessException.invalidField("branchId", "Chi nhánh không thuộc công ty.");
            }
            return Set.of(branch.id());
        }
        if (scope != null) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "Đối chiếu toàn công ty cần phạm vi toàn công ty; chọn "
                    + "chi nhánh.");
        }
        return null;
    }

    private PartnerInfo partner(Long companyId, Long partnerId) {
        return access.partners(List.of(partnerId)).values().stream()
                .filter(found -> found.companyId().equals(companyId))
                .findFirst().orElseThrow(BusinessException::notFound);
    }

    private List<DebtItemResponse> openItems(Long companyId, DebtType debtType, PartnerInfo partner,
                                             Collection<Long> branches) {
        LocalDate today = access.today();
        Specification<DebtItem> spec = (root, query, cb) -> cb.and(cb.equal(root.get("companyId"), companyId),
                cb.equal(root.get("debtType"), debtType), cb.equal(root.get("partnerId"), partner.id()),
                root.get("status").in(DebtItemStatus.OPEN, DebtItemStatus.PARTIAL),
                branches == null ? cb.conjunction() : root.get("branchId").in(branches));
        return itemRepository.findAll(spec, Sort.by("dueDate", "id")).stream()
                .map(item -> DebtQueryService.toResponse(item, partner, today)).toList();
    }

    private DebtReconciliation lock(CurrentUser actor, Long id) {
        return visible(actor, repository.findByIdForUpdate(id).orElseThrow(BusinessException::notFound));
    }

    /** Biên bản toàn công ty chỉ người có phạm vi toàn công ty xem được; ngoài phạm vi trả 404. */
    private DebtReconciliation visible(CurrentUser actor, DebtReconciliation reconciliation) {
        boolean ok = reconciliation.getBranchId() == null
                ? actor.dataScope().hasFullCompanyAccess(reconciliation.getCompanyId())
                : access.canAccess(actor, reconciliation.getCompanyId(), reconciliation.getBranchId());
        if (!ok) {
            throw BusinessException.notFound();
        }
        return reconciliation;
    }

    private void audit(CurrentUser actor, String action, DebtReconciliation reconciliation, String reason) {
        access.audit(actor, action, ENTITY, reconciliation.getId(), reconciliation.getDocumentNo(), reason, null,
                reconciliation.getStatus(), reconciliation.getCompanyId(), reconciliation.getBranchId());
    }

    private static DebtReconciliationResponse toResponse(DebtReconciliation item, PartnerInfo partner) {
        return new DebtReconciliationResponse(item.getId(), item.getCompanyId(), item.getBranchId(),
                item.getDocumentNo(), item.getDebtType(), item.getPartnerId(),
                partner == null ? null : partner.code(), partner == null ? null : partner.name(),
                item.getPeriodFrom(), item.getAsOfDate(), item.getSystemBalance(), item.getConfirmedBalance(),
                item.difference(), item.getStatus(), item.getContactName(), item.getConfirmedAt(), item.getNote(),
                item.getCreatedBy(), item.getCreatedAt(), item.getVersion());
    }

    private static String trim(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
