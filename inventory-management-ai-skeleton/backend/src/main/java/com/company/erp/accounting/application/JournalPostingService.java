package com.company.erp.accounting.application;

import com.company.erp.accounting.domain.Account;
import com.company.erp.accounting.domain.JournalEntry;
import com.company.erp.accounting.domain.JournalEntryKind;
import com.company.erp.accounting.domain.JournalLineDraft;
import com.company.erp.accounting.domain.PostingRule;
import com.company.erp.accounting.infrastructure.persistence.AccountRepository;
import com.company.erp.accounting.infrastructure.persistence.JournalEntryRepository;
import com.company.erp.accounting.infrastructure.persistence.PostingRuleRepository;
import com.company.erp.common.application.DocumentNumberRequest;
import com.company.erp.common.application.DocumentNumberService;
import com.company.erp.common.domain.BusinessException;
import com.company.erp.common.domain.ErrorCode;
import com.company.erp.common.domain.PeriodModule;
import com.company.erp.system.application.OrganizationQueryService;
import com.company.erp.system.application.PeriodGuard;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * API công khai ghi bút toán (BR-ACC-02, 03, ADR-11): module nghiệp vụ gọi trong transaction ghi sổ của chứng từ
 * nguồn, truyền dòng hạch toán theo mã nghiệp vụ; dịch vụ tra quy tắc hạch toán ra tài khoản, kiểm tra kỳ kế toán,
 * tài khoản, cân Nợ = Có rồi ghi. Lỗi bất kỳ làm rollback cả chứng từ nguồn (BR-GEN-20).
 */
@Service
public class JournalPostingService {

    static final String DOCUMENT_TYPE = "BT";

    /**
     * Một dòng hạch toán của chứng từ nguồn.
     *
     * @param transactionCode mã nghiệp vụ tra quy tắc hạch toán (ví dụ {@code RECEIPT_CUSTOMER})
     * @param productType     loại hàng (điều kiện quy tắc), có thể {@code null}
     * @param partnerRole     vai trò đối tượng (điều kiện quy tắc), có thể {@code null}
     * @param debitAccountId  tài khoản Nợ do chứng từ quyết định; chỉ dùng khi quy tắc để trống vế Nợ
     * @param creditAccountId tài khoản Có do chứng từ quyết định; chỉ dùng khi quy tắc để trống vế Có
     */
    public record PostingLine(String transactionCode, BigDecimal amount, String productType, String partnerRole,
                              Long debitAccountId, Long creditAccountId, Long partnerId, Long productId,
                              Long warehouseId, Long departmentId, Long employeeId, Long sourceLineId,
                              String description) {
    }

    public record PostingRequest(Long companyId, Long branchId, LocalDate postingDate, String sourceType,
                                 Long sourceId, String sourceNo, String description, Long actorId,
                                 List<PostingLine> lines) {
    }

    /** Đảo bút toán gốc của chứng từ nguồn vào ngày hạch toán {@code postingDate}. */
    public record ReversalRequest(Long companyId, String sourceType, Long sourceId, LocalDate postingDate,
                                  String description, Long actorId) {
    }

    public record JournalEntryRef(Long id, String entryNo) {
    }

    private final PostingRuleRepository ruleRepository;
    private final AccountRepository accountRepository;
    private final JournalEntryRepository journalRepository;
    private final PeriodGuard periodGuard;
    private final DocumentNumberService numberService;
    private final OrganizationQueryService organization;
    private final Clock clock;

    public JournalPostingService(PostingRuleRepository ruleRepository, AccountRepository accountRepository,
                                 JournalEntryRepository journalRepository, PeriodGuard periodGuard,
                                 DocumentNumberService numberService, OrganizationQueryService organization,
                                 Clock clock) {
        this.ruleRepository = ruleRepository;
        this.accountRepository = accountRepository;
        this.journalRepository = journalRepository;
        this.periodGuard = periodGuard;
        this.numberService = numberService;
        this.organization = organization;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public JournalEntryRef post(PostingRequest request) {
        if (request.lines() == null || request.lines().isEmpty()) {
            throw new IllegalArgumentException("Chứng từ " + request.sourceNo() + " không có dòng hạch toán");
        }
        periodGuard.assertOpen(request.companyId(), request.branchId(), PeriodModule.ACCOUNTING,
                request.postingDate());
        if (journalRepository.existsOriginal(request.companyId(), request.sourceType(), request.sourceId())) {
            throw new BusinessException(ErrorCode.DUPLICATE_DOCUMENT, "Chứng từ " + request.sourceNo()
                    + " đã có bút toán.");
        }
        List<JournalLineDraft> drafts = new ArrayList<>();
        for (PostingLine line : request.lines()) {
            PostingRule rule = resolveRule(request, line);
            drafts.add(draft(line, rule, true, side(rule.getDebitAccountId(), line.debitAccountId(), line, "Nợ")));
            drafts.add(draft(line, rule, false, side(rule.getCreditAccountId(), line.creditAccountId(), line, "Có")));
        }
        requireAccounts(request.companyId(), drafts);
        return save(new JournalEntry.Header(request.companyId(), request.branchId(), null, request.postingDate(),
                JournalEntryKind.AUTO, request.sourceType(), request.sourceId(), request.sourceNo(),
                request.description(), null, request.actorId(), Instant.now(clock)), drafts);
    }

    /** Đảo bút toán gốc: dòng ngược đúng từng dòng (BR-GEN-21); bút toán gốc chuyển REVERSED. */
    @Transactional(propagation = Propagation.MANDATORY)
    public JournalEntryRef reverse(ReversalRequest request) {
        JournalEntry original = journalRepository.findOriginalForUpdate(request.companyId(), request.sourceType(),
                        request.sourceId())
                .orElseThrow(() -> new IllegalStateException("Chứng từ " + request.sourceType() + "#"
                        + request.sourceId() + " chưa có bút toán để đảo"));
        periodGuard.assertOpen(original.getCompanyId(), original.getBranchId(), PeriodModule.ACCOUNTING,
                request.postingDate());
        Instant now = Instant.now(clock);
        original.markReversed(now);
        journalRepository.saveAndFlush(original);
        return save(new JournalEntry.Header(original.getCompanyId(), original.getBranchId(), null,
                request.postingDate(), original.getEntryKind(), original.getSourceType(), original.getSourceId(),
                original.getSourceNo(), request.description(), original.getId(), request.actorId(), now),
                original.reversalLines());
    }

    private JournalEntryRef save(JournalEntry.Header header, List<JournalLineDraft> drafts) {
        String entryNo = nextNumber(header.companyId(), header.branchId(), header.postingDate());
        JournalEntry.Header numbered = new JournalEntry.Header(header.companyId(), header.branchId(), entryNo,
                header.postingDate(), header.entryKind(), header.sourceType(), header.sourceId(), header.sourceNo(),
                header.description(), header.reversalOfId(), header.actorId(), header.now());
        JournalEntry entry = journalRepository.saveAndFlush(JournalEntry.create(numbered, drafts));
        return new JournalEntryRef(entry.getId(), entry.getEntryNo());
    }

    /**
     * Quy tắc áp dụng: đúng loại chứng từ, mã nghiệp vụ, còn hiệu lực ngày hạch toán, khớp điều kiện; nhiều quy tắc
     * khớp thì lấy quy tắc cụ thể nhất, rồi quy tắc bắt đầu hiệu lực muộn nhất.
     */
    private PostingRule resolveRule(PostingRequest request, PostingLine line) {
        return ruleRepository.findByCompanyIdAndDocumentTypeAndTransactionCode(request.companyId(),
                        request.sourceType(), line.transactionCode()).stream()
                .filter(rule -> rule.appliesOn(request.postingDate()))
                .filter(rule -> rule.matches(line.productType(), line.partnerRole()))
                .max(Comparator.comparingInt(PostingRule::specificity)
                        .thenComparing(PostingRule::getValidFrom))
                .orElseThrow(() -> new BusinessException(ErrorCode.POSTING_RULE_NOT_FOUND,
                        "Chưa có quy tắc hạch toán còn hiệu lực cho chứng từ " + request.sourceType()
                                + ", nghiệp vụ " + line.transactionCode() + " ngày " + request.postingDate() + "."));
    }

    private static Long side(Long ruleAccountId, Long documentAccountId, PostingLine line, String label) {
        Long accountId = ruleAccountId != null ? ruleAccountId : documentAccountId;
        if (accountId == null) {
            throw new BusinessException(ErrorCode.POSTING_RULE_INCOMPLETE, "Quy tắc hạch toán nghiệp vụ "
                    + line.transactionCode() + " để trống tài khoản " + label + " nhưng chứng từ không truyền tài khoản.");
        }
        return accountId;
    }

    private static JournalLineDraft draft(PostingLine line, PostingRule rule, boolean debit, Long accountId) {
        return new JournalLineDraft(accountId, debit, line.amount(), line.transactionCode(), rule.getId(),
                line.partnerId(), line.productId(), line.warehouseId(), line.departmentId(), line.employeeId(),
                line.sourceLineId(), line.description());
    }

    /**
     * Tài khoản phải thuộc công ty, đang hoạt động, là tài khoản chi tiết; tài khoản theo dõi đối tượng phải có đối
     * tượng. Đọc với khóa chia sẻ theo id tăng dần (thêm tài khoản con khóa ghi tài khoản cha).
     */
    private void requireAccounts(Long companyId, List<JournalLineDraft> drafts) {
        TreeSet<Long> ids = drafts.stream().map(JournalLineDraft::accountId)
                .collect(Collectors.toCollection(TreeSet::new));
        Map<Long, Account> accounts = accountRepository.findForShare(ids).stream()
                .collect(Collectors.toMap(Account::getId, Function.identity()));
        for (JournalLineDraft draft : drafts) {
            Account account = accounts.get(draft.accountId());
            if (account == null || !Objects.equals(account.getCompanyId(), companyId)) {
                throw new BusinessException(ErrorCode.POSTING_RULE_INCOMPLETE, "Tài khoản #" + draft.accountId()
                        + " không thuộc công ty của chứng từ.");
            }
            if (!account.isActive() || !account.isPostable()) {
                throw new BusinessException(ErrorCode.ACCOUNT_NOT_POSTABLE, "Tài khoản " + account.getCode()
                        + " đã ngừng hoạt động hoặc là tài khoản tổng hợp.");
            }
            if (account.isPartnerRequired() && draft.partnerId() == null) {
                throw new BusinessException(ErrorCode.ACCOUNT_NOT_POSTABLE, "Tài khoản " + account.getCode()
                        + " bắt buộc chi tiết theo đối tượng; chứng từ thiếu đối tượng.");
            }
        }
    }

    private String nextNumber(Long companyId, Long branchId, LocalDate postingDate) {
        String branchCode = organization.branch(branchId)
                .map(OrganizationQueryService.BranchRef::code)
                .orElseThrow(() -> new IllegalStateException("Thiếu chi nhánh #" + branchId));
        return numberService.nextNumber(new DocumentNumberRequest(companyId, branchId, branchCode, DOCUMENT_TYPE,
                postingDate));
    }
}
