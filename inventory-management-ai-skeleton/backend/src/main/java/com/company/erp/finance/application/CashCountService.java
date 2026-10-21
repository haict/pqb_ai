package com.company.erp.finance.application;

import com.company.erp.common.domain.BusinessException;
import com.company.erp.finance.application.dto.CashCountRequest;
import com.company.erp.finance.application.dto.CashCountResponse;
import com.company.erp.finance.application.dto.ReasonRequest;
import com.company.erp.finance.domain.CashAccount;
import com.company.erp.finance.domain.CashAccountType;
import com.company.erp.finance.domain.CashCount;
import com.company.erp.finance.domain.FinanceDocumentStatus;
import com.company.erp.finance.infrastructure.persistence.CashAccountRepository;
import com.company.erp.finance.infrastructure.persistence.CashCountRepository;
import com.company.erp.finance.infrastructure.persistence.CashLedgerEntryRepository;
import com.company.erp.system.application.ApprovalService;
import com.company.erp.system.application.CurrentUser;
import com.company.erp.system.application.OrganizationQueryService.BranchRef;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Lập, sửa, hủy phiếu kiểm kê quỹ tiền mặt (KKQ, BR-CSH-05; D-61 không qua duyệt). Ghi sổ chênh lệch ở
 * {@link CashCountPostingService}.
 */
@Service
public class CashCountService {

    static final String PATH = "/api/v1/cash-counts";
    static final String VIEW = "cash-count:view";
    static final String CREATE = "cash-count:create";
    static final String ENTITY = "CASH_COUNT";

    public record CashCountFilter(Long companyId, Long branchId, Long cashAccountId, FinanceDocumentStatus status,
                                  LocalDate dateFrom, LocalDate dateTo, String keyword) {
    }

    private final CashCountRepository repository;
    private final CashAccountRepository accountRepository;
    private final CashLedgerEntryRepository ledgerRepository;
    private final FinanceDocumentFlow flow;
    private final FinanceAccess access;
    private final ApprovalService approvalService;
    private final FinanceDocumentFlow.Spec<CashCount, CashCountResponse> spec;

    public CashCountService(CashCountRepository repository, CashAccountRepository accountRepository,
                            CashLedgerEntryRepository ledgerRepository, FinanceDocumentFlow flow,
                            FinanceAccess access, ApprovalService approvalService) {
        this.repository = repository;
        this.accountRepository = accountRepository;
        this.ledgerRepository = ledgerRepository;
        this.flow = flow;
        this.access = access;
        this.approvalService = approvalService;
        this.spec = new FinanceDocumentFlow.Spec<>(PATH, VIEW, CREATE, ENTITY,
                repository::findByIdForUpdate, repository, count -> toResponse(count, true), CashCountResponse.class);
    }

    @Transactional(readOnly = true)
    public Page<CashCountResponse> search(CurrentUser actor, CashCountFilter filter, Pageable pageable) {
        Page<CashCount> page = flow.search(actor, VIEW, repository, new FinanceDocumentFlow.Filter<CashCount>(
                filter.companyId(), filter.branchId(), filter.status(), null, filter.dateFrom(), filter.dateTo(),
                filter.keyword(), filter.cashAccountId() == null ? null
                : (root, query, cb) -> cb.equal(root.get("cashAccountId"), filter.cashAccountId())), pageable);
        Map<Long, CashAccount> accounts = accountRepository.findAllById(page.getContent().stream()
                        .map(CashCount::getCashAccountId).collect(Collectors.toSet())).stream()
                .collect(Collectors.toMap(CashAccount::getId, Function.identity()));
        return page.map(count -> toResponse(count, accounts.get(count.getCashAccountId()), false));
    }

    @Transactional(readOnly = true)
    public CashCountResponse get(CurrentUser actor, Long id) {
        access.requirePermission(actor, VIEW);
        return toResponse(flow.visible(actor, repository.findById(id).orElseThrow(BusinessException::notFound)), true);
    }

    @Transactional
    public CashCountResponse create(CurrentUser actor, String key, CashCountRequest request) {
        return flow.idempotent(actor, key, "POST " + PATH, request, CashCountResponse.class, () -> {
            access.requirePermission(actor, CREATE);
            BranchRef branch = access.usableBranch(actor, request.branchId());
            CashCount count = new CashCount(branch.companyId(), branch.id(), access.nextNumber(branch.companyId(),
                    branch.id(), CashCount.TYPE_CODE, request.documentDate()));
            fill(count, request);
            count = repository.saveAndFlush(count);
            return flow.act(actor, count, "CREATE", null, spec);
        });
    }

    @Transactional
    public CashCountResponse update(CurrentUser actor, Long id, CashCountRequest request) {
        access.requirePermission(actor, CREATE);
        CashCount count = flow.lock(actor, id, spec);
        FinanceDocumentFlow.requireVersion(count, request.version());
        if (!count.getBranchId().equals(request.branchId())) {
            throw BusinessException.invalidField("branchId", "Không đổi chi nhánh sau khi lập.");
        }
        fill(count, request);
        return flow.act(actor, count, "UPDATE", null, spec);
    }

    @Transactional
    public CashCountResponse cancel(CurrentUser actor, Long id, String key, ReasonRequest request) {
        return flow.cancel(actor, id, key, request, spec);
    }

    FinanceDocumentFlow.Spec<CashCount, CashCountResponse> spec() {
        return spec;
    }

    /** Quỹ kiểm kê: quỹ tiền mặt cùng chi nhánh, đang hoạt động. */
    private void fill(CashCount count, CashCountRequest request) {
        CashAccount account = accountRepository.findById(request.cashAccountId())
                .filter(found -> found.getCompanyId().equals(count.getCompanyId())
                        && found.getBranchId().equals(count.getBranchId()) && found.isActive()
                        && found.getAccountType() == CashAccountType.CASH)
                .orElseThrow(() -> BusinessException.invalidField("cashAccountId", "Chọn quỹ tiền mặt đang hoạt động "
                        + "của chi nhánh."));
        LocalDate postingDate = request.postingDate() != null ? request.postingDate() : request.documentDate();
        count.replaceContent(request.documentDate(), postingDate, trim(request.description()), account.getId(),
                request.countedAmount());
    }

    /** Số dư sổ quỹ đến hết ngày kiểm kê. */
    BigDecimal bookBalance(CashCount count) {
        return ledgerRepository.netBefore(count.getCashAccountId(), count.getDocumentDate().plusDays(1));
    }

    private CashCountResponse toResponse(CashCount count, boolean detailed) {
        return toResponse(count, accountRepository.findById(count.getCashAccountId()).orElse(null), detailed);
    }

    private CashCountResponse toResponse(CashCount count, CashAccount account, boolean detailed) {
        return new CashCountResponse(count.getId(), count.getCompanyId(), count.getBranchId(), count.getDocumentNo(),
                count.getCashAccountId(), account == null ? null : account.getCode(),
                account == null ? null : account.getName(), count.getDocumentDate(), count.getPostingDate(),
                count.getDescription(), count.getCountedAmount(), count.getBookBalance(), count.getDifferenceAmount(),
                detailed ? bookBalance(count) : null, count.getStatus(), count.getSubmittedAt(),
                count.getApprovedAt(), count.getPostedAt(), count.getReversedAt(), count.getReverseDate(),
                count.getReason(), count.getCreatedBy(), count.getVersion(),
                detailed ? approvalService.history(CashCount.TYPE_CODE, count.getId()) : List.of());
    }

    private static String trim(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
