package com.company.erp.accounting.application;

import com.company.erp.accounting.application.dto.PostingRuleRequest;
import com.company.erp.accounting.application.dto.PostingRuleResponse;
import com.company.erp.accounting.application.dto.UpdatePostingRuleRequest;
import com.company.erp.accounting.domain.Account;
import com.company.erp.accounting.domain.PostingRule;
import com.company.erp.accounting.infrastructure.persistence.AccountRepository;
import com.company.erp.accounting.infrastructure.persistence.PostingRuleRepository;
import com.company.erp.common.api.PageRequests;
import com.company.erp.common.application.IdempotencyService;
import com.company.erp.common.application.IdempotentCommand;
import com.company.erp.common.domain.BusinessException;
import com.company.erp.common.domain.ErrorCode;
import com.company.erp.common.domain.RecordStatus;
import com.company.erp.masterdata.application.MasterDataQueryService;
import com.company.erp.system.application.CurrentUser;
import com.company.erp.system.application.DocumentTypeService;
import com.company.erp.system.application.dto.ChangeStatusRequest;
import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Quy tắc hạch toán cấu hình (BR-ACC-02, ADR-11). */
@Service
public class PostingRuleService {

    static final String VIEW = "posting-rule:view";
    static final String MANAGE = "posting-rule:manage";
    private static final Set<String> SORTABLE = Set.of("documentType", "transactionCode", "validFrom");

    private final PostingRuleRepository repository;
    private final AccountRepository accountRepository;
    private final DocumentTypeService documentTypes;
    private final AccountingAccess access;
    private final IdempotencyService idempotency;

    public PostingRuleService(PostingRuleRepository repository, AccountRepository accountRepository,
                              DocumentTypeService documentTypes, AccountingAccess access,
                              IdempotencyService idempotency) {
        this.repository = repository;
        this.accountRepository = accountRepository;
        this.documentTypes = documentTypes;
        this.access = access;
        this.idempotency = idempotency;
    }

    @Transactional(readOnly = true)
    public Page<PostingRuleResponse> search(CurrentUser actor, Long companyId, String documentType,
                                            String transactionCode, RecordStatus status, Pageable pageable) {
        access.requirePermission(actor, VIEW);
        access.requireCompany(actor, companyId);
        Specification<PostingRule> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("companyId"), companyId));
            if (documentType != null && !documentType.isBlank()) {
                predicates.add(cb.equal(root.get("documentType"), documentType.trim()));
            }
            if (transactionCode != null && !transactionCode.isBlank()) {
                predicates.add(cb.like(root.get("transactionCode"), "%" + transactionCode.trim().toUpperCase() + "%"));
            }
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
        Page<PostingRule> page = repository.findAll(spec, PageRequests.checked(pageable, SORTABLE,
                Sort.by("documentType", "transactionCode", "validFrom")));
        Map<Long, String> codes = accountCodes(page.getContent());
        return page.map(rule -> toResponse(rule, codes));
    }

    @Transactional
    public PostingRuleResponse create(CurrentUser actor, String idempotencyKey, PostingRuleRequest request) {
        IdempotentCommand command = new IdempotentCommand(actor.id(), idempotencyKey, "POST /api/v1/posting-rules",
                request);
        return idempotency.execute(command, PostingRuleResponse.class, () -> {
            access.requirePermission(actor, MANAGE);
            access.requireCompany(actor, request.companyId());
            String documentType = request.documentType().trim();
            if (documentTypes.list().stream().noneMatch(type -> type.code().equals(documentType))) {
                throw BusinessException.invalidField("documentType", "Loại chứng từ không tồn tại.");
            }
            String productType = blankToNull(request.productType());
            String partnerRole = blankToNull(request.partnerRole());
            if (productType != null && !MasterDataQueryService.isProductType(productType)) {
                throw BusinessException.invalidField("productType", "Loại hàng không hợp lệ.");
            }
            if (partnerRole != null && !MasterDataQueryService.isPartnerRole(partnerRole)) {
                throw BusinessException.invalidField("partnerRole", "Vai trò đối tượng không hợp lệ.");
            }
            if (repository.existsCondition(request.companyId(), documentType, request.transactionCode(),
                    productType == null ? "" : productType, partnerRole == null ? "" : partnerRole,
                    request.validFrom())) {
                throw new BusinessException(ErrorCode.DUPLICATE_CODE,
                        "Đã có quy tắc cùng điều kiện và ngày bắt đầu hiệu lực.");
            }
            requireAccounts(request.companyId(), request.debitAccountId(), request.creditAccountId());
            PostingRule rule = new PostingRule(request.companyId(), documentType, request.transactionCode(),
                    productType, partnerRole, request.validFrom());
            rule.configure(request.debitAccountId(), request.creditAccountId(), request.validTo(),
                    blankToNull(request.description()));
            rule = repository.saveAndFlush(rule);
            PostingRuleResponse response = toResponse(rule, accountCodes(List.of(rule)));
            access.audit(actor, "CREATE", AccountingAccess.ENTITY_POSTING_RULE, rule.getId(), null, null, response,
                    rule.getCompanyId());
            return response;
        });
    }

    @Transactional
    public PostingRuleResponse update(CurrentUser actor, Long id, UpdatePostingRuleRequest request) {
        access.requirePermission(actor, MANAGE);
        PostingRule rule = visible(actor, id);
        rule.verifyVersion(request.version());
        PostingRuleResponse before = toResponse(rule, accountCodes(List.of(rule)));
        requireAccounts(rule.getCompanyId(), request.debitAccountId(), request.creditAccountId());
        rule.configure(request.debitAccountId(), request.creditAccountId(), request.validTo(),
                blankToNull(request.description()));
        rule = repository.saveAndFlush(rule);
        PostingRuleResponse after = toResponse(rule, accountCodes(List.of(rule)));
        access.audit(actor, "UPDATE", AccountingAccess.ENTITY_POSTING_RULE, id, null, before, after,
                rule.getCompanyId());
        return after;
    }

    @Transactional
    public PostingRuleResponse changeStatus(CurrentUser actor, Long id, ChangeStatusRequest request) {
        access.requirePermission(actor, MANAGE);
        PostingRule rule = visible(actor, id);
        rule.verifyVersion(request.version());
        PostingRuleResponse before = toResponse(rule, accountCodes(List.of(rule)));
        if (request.status() == RecordStatus.ACTIVE) {
            requireAccounts(rule.getCompanyId(), rule.getDebitAccountId(), rule.getCreditAccountId());
        }
        rule.changeStatus(request.status());
        rule = repository.saveAndFlush(rule);
        PostingRuleResponse after = toResponse(rule, accountCodes(List.of(rule)));
        access.audit(actor, "CHANGE_STATUS", AccountingAccess.ENTITY_POSTING_RULE, id, request.reason(), before,
                after, rule.getCompanyId());
        return after;
    }

    /** Tài khoản trong quy tắc phải thuộc công ty, đang hoạt động và là tài khoản chi tiết. */
    private void requireAccounts(Long companyId, Long debitAccountId, Long creditAccountId) {
        requireAccount(companyId, debitAccountId, "debitAccountId");
        requireAccount(companyId, creditAccountId, "creditAccountId");
    }

    private void requireAccount(Long companyId, Long accountId, String field) {
        if (accountId == null) {
            return;
        }
        Account account = accountRepository.findById(accountId)
                .filter(found -> found.getCompanyId().equals(companyId))
                .orElseThrow(() -> BusinessException.invalidField(field, "Tài khoản không tồn tại."));
        if (!account.isActive() || !account.isPostable()) {
            throw BusinessException.invalidField(field, "Tài khoản " + account.getCode()
                    + " đã ngừng hoạt động hoặc là tài khoản tổng hợp (có tài khoản con).");
        }
    }

    private PostingRule visible(CurrentUser actor, Long id) {
        PostingRule rule = repository.findById(id).orElseThrow(BusinessException::notFound);
        access.requireCompany(actor, rule.getCompanyId());
        return rule;
    }

    private Map<Long, String> accountCodes(List<PostingRule> rules) {
        Set<Long> ids = new HashSet<>();
        rules.forEach(rule -> {
            ids.add(rule.getDebitAccountId());
            ids.add(rule.getCreditAccountId());
        });
        ids.remove(null);
        return accountRepository.findByIdIn(ids).stream()
                .collect(Collectors.toMap(Account::getId, Account::getCode));
    }

    private static PostingRuleResponse toResponse(PostingRule rule, Map<Long, String> codes) {
        return new PostingRuleResponse(rule.getId(), rule.getCompanyId(), rule.getDocumentType(),
                rule.getTransactionCode(), rule.getProductType(), rule.getPartnerRole(), rule.getDebitAccountId(),
                codes.get(rule.getDebitAccountId()), rule.getCreditAccountId(), codes.get(rule.getCreditAccountId()),
                rule.getValidFrom(), rule.getValidTo(), rule.getDescription(), rule.getStatus(), rule.getVersion(),
                rule.getUpdatedAt());
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
