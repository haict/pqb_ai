package com.company.erp.accounting.application;

import com.company.erp.accounting.application.dto.AccountRequest;
import com.company.erp.accounting.application.dto.AccountResponse;
import com.company.erp.accounting.application.dto.UpdateAccountRequest;
import com.company.erp.accounting.domain.Account;
import com.company.erp.accounting.domain.BalanceSide;
import com.company.erp.accounting.infrastructure.persistence.AccountRepository;
import com.company.erp.accounting.infrastructure.persistence.JournalEntryRepository;
import com.company.erp.accounting.infrastructure.persistence.PostingRuleRepository;
import com.company.erp.common.api.PageRequests;
import com.company.erp.common.application.IdempotencyService;
import com.company.erp.common.application.IdempotentCommand;
import com.company.erp.common.domain.BusinessException;
import com.company.erp.common.domain.ErrorCode;
import com.company.erp.common.domain.RecordStatus;
import com.company.erp.system.application.CurrentUser;
import com.company.erp.system.application.dto.ChangeStatusRequest;
import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Hệ thống tài khoản (BR-ACC-01): thêm, sửa, ngừng; nhập từ Excel ở {@link AccountImportService}. */
@Service
public class AccountService {

    static final String VIEW = "account:view";
    static final String MANAGE = "account:manage";
    private static final Set<String> SORTABLE = Set.of("code", "name");

    private final AccountRepository repository;
    private final JournalEntryRepository journalRepository;
    private final PostingRuleRepository ruleRepository;
    private final AccountingAccess access;
    private final IdempotencyService idempotency;

    public AccountService(AccountRepository repository, JournalEntryRepository journalRepository,
                          PostingRuleRepository ruleRepository, AccountingAccess access,
                          IdempotencyService idempotency) {
        this.repository = repository;
        this.journalRepository = journalRepository;
        this.ruleRepository = ruleRepository;
        this.access = access;
        this.idempotency = idempotency;
    }

    @Transactional(readOnly = true)
    public Page<AccountResponse> search(CurrentUser actor, Long companyId, String keyword, RecordStatus status,
                                        Boolean postable, Pageable pageable) {
        access.requirePermission(actor, VIEW);
        access.requireCompany(actor, companyId);
        Specification<Account> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("companyId"), companyId));
            if (keyword != null && !keyword.isBlank()) {
                String like = "%" + keyword.trim().toLowerCase() + "%";
                predicates.add(cb.or(cb.like(cb.lower(root.get("code")), like),
                        cb.like(cb.lower(root.get("name")), like)));
            }
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            if (postable != null) {
                predicates.add(cb.equal(root.get("postable"), postable));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
        Page<Account> page = repository.findAll(spec, PageRequests.checked(pageable, SORTABLE, Sort.by("code")));
        Map<Long, String> parentCodes = codes(page.getContent().stream().map(Account::getParentId).toList());
        return page.map(account -> toResponse(account, parentCodes.get(account.getParentId())));
    }

    /**
     * Toàn bộ hệ thống tài khoản của công ty theo thứ tự mã, để hiển thị dạng cây. Số tài khoản của một công ty có
     * giới hạn (vài trăm) nên không phân trang.
     */
    @Transactional(readOnly = true)
    public List<AccountResponse> tree(CurrentUser actor, Long companyId) {
        access.requirePermission(actor, VIEW);
        access.requireCompany(actor, companyId);
        List<Account> accounts = repository.findByCompanyId(companyId);
        Map<Long, String> codes = accounts.stream().collect(Collectors.toMap(Account::getId, Account::getCode));
        return accounts.stream()
                .sorted(Comparator.comparing(Account::getCode))
                .map(account -> toResponse(account, codes.get(account.getParentId())))
                .toList();
    }

    @Transactional
    public AccountResponse create(CurrentUser actor, String idempotencyKey, AccountRequest request) {
        IdempotentCommand command = new IdempotentCommand(actor.id(), idempotencyKey, "POST /api/v1/accounts",
                request);
        return idempotency.execute(command, AccountResponse.class, () -> {
            access.requirePermission(actor, MANAGE);
            access.requireCompany(actor, request.companyId());
            Account parent = request.parentId() == null ? null
                    : lockParent(request.companyId(), request.parentId());
            Account account = createAccount(request.companyId(), request.code(), request.name().trim(), parent,
                    request.balanceSide(), request.partnerRequired());
            AccountResponse response = toResponse(account, parentCode(account));
            access.audit(actor, "CREATE", AccountingAccess.ENTITY_ACCOUNT, account.getId(), null, null, response,
                    account.getCompanyId());
            return response;
        });
    }

    @Transactional
    public AccountResponse update(CurrentUser actor, Long id, UpdateAccountRequest request) {
        access.requirePermission(actor, MANAGE);
        Account account = visible(actor, id);
        account.verifyVersion(request.version());
        AccountResponse before = toResponse(account, parentCode(account));
        account.update(request.name().trim(), request.balanceSide(), request.partnerRequired());
        AccountResponse after = toResponse(repository.saveAndFlush(account), parentCode(account));
        access.audit(actor, "UPDATE", AccountingAccess.ENTITY_ACCOUNT, id, null, before, after,
                account.getCompanyId());
        return after;
    }

    @Transactional
    public AccountResponse changeStatus(CurrentUser actor, Long id, ChangeStatusRequest request) {
        access.requirePermission(actor, MANAGE);
        Account account = visible(actor, id);
        account.verifyVersion(request.version());
        if (request.status() == RecordStatus.INACTIVE) {
            if (repository.existsByParentIdAndStatus(id, RecordStatus.ACTIVE)) {
                throw new BusinessException(ErrorCode.DATA_IN_USE, "Tài khoản " + account.getCode()
                        + " còn tài khoản con đang hoạt động; ngừng tài khoản con trước.");
            }
            requireNoActiveRule(account);
        } else if (account.getParentId() != null
                && !repository.findById(account.getParentId()).map(Account::isActive).orElse(false)) {
            throw new BusinessException(ErrorCode.DATA_IN_USE, "Tài khoản cha đang ngừng hoạt động.");
        }
        AccountResponse before = toResponse(account, parentCode(account));
        account.changeStatus(request.status());
        AccountResponse after = toResponse(repository.saveAndFlush(account), parentCode(account));
        access.audit(actor, "CHANGE_STATUS", AccountingAccess.ENTITY_ACCOUNT, id, request.reason(), before, after,
                account.getCompanyId());
        return after;
    }

    /** Tạo tài khoản; tài khoản cha (đã khóa ghi) chuyển thành tài khoản tổng hợp. Dùng chung cho nhập Excel. */
    Account createAccount(Long companyId, String code, String name, Account parent, BalanceSide balanceSide,
                          boolean partnerRequired) {
        if (repository.existsByCompanyIdAndCode(companyId, code)) {
            throw new BusinessException(ErrorCode.DUPLICATE_CODE, "Tài khoản '" + code + "' đã tồn tại.");
        }
        if (parent != null && parent.isPostable() && journalRepository.existsLineForAccount(parent.getId())) {
            throw new BusinessException(ErrorCode.DATA_IN_USE, "Tài khoản " + parent.getCode()
                    + " đã phát sinh hạch toán, không thêm tài khoản con được.");
        }
        Account account = repository.saveAndFlush(new Account(companyId, code, name, parent, balanceSide,
                partnerRequired));
        if (parent != null && parent.isPostable()) {
            parent.markHasChildren();
            repository.saveAndFlush(parent);
        }
        return account;
    }

    Account lockParent(Long companyId, Long parentId) {
        return repository.findByIdForUpdate(parentId)
                .filter(parent -> parent.getCompanyId().equals(companyId))
                .orElseThrow(() -> BusinessException.invalidField("parentId", "Tài khoản cha không tồn tại."));
    }

    private void requireNoActiveRule(Account account) {
        boolean active = ruleRepository.findAll((root, query, cb) -> cb.and(
                        cb.equal(root.get("status"), RecordStatus.ACTIVE),
                        cb.or(cb.equal(root.get("debitAccountId"), account.getId()),
                                cb.equal(root.get("creditAccountId"), account.getId()))))
                .stream().findAny().isPresent();
        if (active) {
            throw new BusinessException(ErrorCode.DATA_IN_USE, "Tài khoản " + account.getCode()
                    + " đang được dùng trong quy tắc hạch toán còn hoạt động.");
        }
    }

    private Account visible(CurrentUser actor, Long id) {
        Account account = repository.findById(id).orElseThrow(BusinessException::notFound);
        access.requireCompany(actor, account.getCompanyId());
        return account;
    }

    private String parentCode(Account account) {
        return account.getParentId() == null ? null
                : repository.findById(account.getParentId()).map(Account::getCode).orElse(null);
    }

    private Map<Long, String> codes(List<Long> ids) {
        List<Long> present = ids.stream().filter(Objects::nonNull).distinct().toList();
        return repository.findByIdIn(present).stream()
                .collect(Collectors.toMap(Account::getId, Account::getCode, (a, b) -> a));
    }

    static AccountResponse toResponse(Account account, String parentCode) {
        return new AccountResponse(account.getId(), account.getCompanyId(), account.getCode(), account.getName(),
                account.getParentId(), parentCode, account.getLevel(), account.getBalanceSide(),
                account.isPostable(), account.isPartnerRequired(), account.getStatus(), account.getVersion(),
                account.getUpdatedAt());
    }
}
