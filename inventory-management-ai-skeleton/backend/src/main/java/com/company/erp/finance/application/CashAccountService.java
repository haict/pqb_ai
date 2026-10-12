package com.company.erp.finance.application;

import com.company.erp.accounting.application.AccountLookupService;
import com.company.erp.accounting.application.AccountLookupService.AccountInfo;
import com.company.erp.common.api.PageRequests;
import com.company.erp.common.application.IdempotencyService;
import com.company.erp.common.application.IdempotentCommand;
import com.company.erp.common.domain.BusinessException;
import com.company.erp.common.domain.ErrorCode;
import com.company.erp.common.domain.RecordStatus;
import com.company.erp.finance.application.dto.CashAccountRequest;
import com.company.erp.finance.application.dto.CashAccountResponse;
import com.company.erp.finance.application.dto.UpdateCashAccountRequest;
import com.company.erp.finance.domain.CashAccount;
import com.company.erp.finance.domain.CashAccountType;
import com.company.erp.finance.infrastructure.persistence.CashAccountRepository;
import com.company.erp.system.application.CurrentUser;
import com.company.erp.system.application.OrganizationQueryService.BranchRef;
import com.company.erp.system.application.dto.ChangeStatusRequest;
import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Danh mục quỹ tiền mặt, tài khoản ngân hàng (BR-CSH-01, BR-MD-46). Số dư không sửa được ở đây; chỉ đổi qua ghi sổ
 * chứng từ thu chi (BR-GEN-P2).
 */
@Service
public class CashAccountService {

    static final String VIEW = "cash-account:view";
    static final String MANAGE = "cash-account:manage";
    private static final Set<String> SORTABLE = Set.of("code", "name", "balance");

    public record CashAccountFilter(Long companyId, Long branchId, CashAccountType accountType, RecordStatus status,
                                    String keyword) {
    }

    private final CashAccountRepository repository;
    private final AccountLookupService accounts;
    private final FinanceAccess access;
    private final IdempotencyService idempotency;

    public CashAccountService(CashAccountRepository repository, AccountLookupService accounts, FinanceAccess access,
                              IdempotencyService idempotency) {
        this.repository = repository;
        this.accounts = accounts;
        this.access = access;
        this.idempotency = idempotency;
    }

    @Transactional(readOnly = true)
    public Page<CashAccountResponse> search(CurrentUser actor, CashAccountFilter filter, Pageable pageable) {
        access.requirePermission(actor, VIEW);
        Set<Long> branches = access.branchScope(actor, filter.companyId());
        Specification<CashAccount> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("companyId"), filter.companyId()));
            if (branches != null) {
                predicates.add(root.get("branchId").in(branches));
            }
            if (filter.branchId() != null) {
                predicates.add(cb.equal(root.get("branchId"), filter.branchId()));
            }
            if (filter.accountType() != null) {
                predicates.add(cb.equal(root.get("accountType"), filter.accountType()));
            }
            if (filter.status() != null) {
                predicates.add(cb.equal(root.get("status"), filter.status()));
            }
            if (filter.keyword() != null && !filter.keyword().isBlank()) {
                String like = "%" + filter.keyword().trim().toLowerCase() + "%";
                predicates.add(cb.or(cb.like(cb.lower(root.get("code")), like),
                        cb.like(cb.lower(root.get("name")), like),
                        cb.like(cb.lower(root.get("bankAccountNo")), like)));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
        Page<CashAccount> page = repository.findAll(spec, PageRequests.checked(pageable, SORTABLE,
                Sort.by(Sort.Order.asc("code"))));
        Map<Long, AccountInfo> gl = glAccounts(page.getContent());
        return page.map(account -> toResponse(account, gl.get(account.getGlAccountId())));
    }

    @Transactional(readOnly = true)
    public CashAccountResponse get(CurrentUser actor, Long id) {
        access.requirePermission(actor, VIEW);
        return toResponse(visible(actor, repository.findById(id).orElseThrow(BusinessException::notFound)));
    }

    @Transactional
    public CashAccountResponse create(CurrentUser actor, String idempotencyKey, CashAccountRequest request) {
        IdempotentCommand command = new IdempotentCommand(actor.id(), idempotencyKey, "POST /api/v1/cash-accounts",
                request);
        return idempotency.execute(command, CashAccountResponse.class, () -> {
            access.requirePermission(actor, MANAGE);
            BranchRef branch = access.usableBranch(actor, request.branchId());
            String code = request.code().trim();
            if (repository.existsByCompanyIdAndCode(branch.companyId(), code)) {
                throw new BusinessException(ErrorCode.DUPLICATE_CODE, "Mã quỹ " + code + " đã tồn tại.");
            }
            CashAccount account = new CashAccount(branch.companyId(), branch.id(), code, request.accountType());
            account.update(request.name().trim(), glAccount(branch.companyId(), request.glAccountId()).id(),
                    trim(request.bankName()), trim(request.bankAccountNo()));
            account = repository.saveAndFlush(account);
            audit(actor, "CREATE", account, null);
            return toResponse(account);
        });
    }

    @Transactional
    public CashAccountResponse update(CurrentUser actor, Long id, UpdateCashAccountRequest request) {
        access.requirePermission(actor, MANAGE);
        CashAccount account = visible(actor, repository.findByIdForUpdate(id).orElseThrow(BusinessException::notFound));
        account.verifyVersion(request.version());
        account.update(request.name().trim(), glAccount(account.getCompanyId(), request.glAccountId()).id(),
                trim(request.bankName()), trim(request.bankAccountNo()));
        repository.saveAndFlush(account);
        audit(actor, "UPDATE", account, null);
        return toResponse(account);
    }

    @Transactional
    public CashAccountResponse changeStatus(CurrentUser actor, Long id, ChangeStatusRequest request) {
        access.requirePermission(actor, MANAGE);
        CashAccount account = visible(actor, repository.findByIdForUpdate(id).orElseThrow(BusinessException::notFound));
        account.verifyVersion(request.version());
        account.changeStatus(request.status());
        repository.saveAndFlush(account);
        audit(actor, request.status() == RecordStatus.ACTIVE ? "ACTIVATE" : "DEACTIVATE", account, request.reason());
        return toResponse(account);
    }

    /** Tài khoản tiền phải là tài khoản chi tiết, đang hoạt động, cùng công ty. */
    private AccountInfo glAccount(Long companyId, Long glAccountId) {
        return accounts.account(glAccountId)
                .filter(found -> found.companyId().equals(companyId) && found.usable())
                .orElseThrow(() -> BusinessException.invalidField("glAccountId",
                        "Tài khoản kế toán không tồn tại, đã ngừng hoặc là tài khoản tổng hợp."));
    }

    private CashAccount visible(CurrentUser actor, CashAccount account) {
        if (!access.canAccess(actor, account.getCompanyId(), account.getBranchId())) {
            throw BusinessException.notFound();
        }
        return account;
    }

    private Map<Long, AccountInfo> glAccounts(List<CashAccount> items) {
        return accounts.accounts(items.stream().map(CashAccount::getGlAccountId).collect(Collectors.toSet()))
                .stream().collect(Collectors.toMap(AccountInfo::id, Function.identity()));
    }

    private CashAccountResponse toResponse(CashAccount account) {
        return toResponse(account, accounts.account(account.getGlAccountId()).orElse(null));
    }

    static CashAccountResponse toResponse(CashAccount account, AccountInfo gl) {
        return new CashAccountResponse(account.getId(), account.getCompanyId(), account.getBranchId(),
                account.getCode(), account.getName(), account.getAccountType(), account.getGlAccountId(),
                gl == null ? null : gl.code(), account.getBankName(), account.getBankAccountNo(),
                account.getCurrencyCode(), account.getBalance(), account.getStatus(), account.getVersion());
    }

    private void audit(CurrentUser actor, String action, CashAccount account, String reason) {
        access.audit(actor, action, FinanceAccess.ENTITY_CASH_ACCOUNT, account.getId(), account.getCode(), reason,
                null, account.getName(), account.getCompanyId(), account.getBranchId());
    }

    private static String trim(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
