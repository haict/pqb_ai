package com.company.erp.accounting.application;

import com.company.erp.accounting.application.dto.JournalEntryLineResponse;
import com.company.erp.accounting.application.dto.JournalEntryResponse;
import com.company.erp.accounting.application.dto.JournalEntrySummaryResponse;
import com.company.erp.accounting.domain.Account;
import com.company.erp.accounting.domain.JournalEntry;
import com.company.erp.accounting.domain.JournalEntryLine;
import com.company.erp.accounting.domain.JournalEntryStatus;
import com.company.erp.accounting.infrastructure.persistence.AccountRepository;
import com.company.erp.accounting.infrastructure.persistence.JournalEntryRepository;
import com.company.erp.common.api.PageRequests;
import com.company.erp.common.domain.BusinessException;
import com.company.erp.masterdata.application.MasterDataQueryService;
import com.company.erp.masterdata.application.MasterDataQueryService.PartnerInfo;
import com.company.erp.system.application.CurrentUser;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Tra cứu bút toán (chỉ đọc, quyền {@code journal:view}), lọc theo phạm vi chi nhánh của người dùng. */
@Service
public class JournalQueryService {

    static final String VIEW = "journal:view";
    private static final Set<String> SORTABLE = Set.of("postingDate", "entryNo", "createdAt");
    /** Số chứng từ nguồn mỗi câu truy vấn IN, giữ câu SQL ở kích thước hợp lý. */
    private static final int SOURCE_BATCH = 500;

    /** Mã tài khoản Nợ / Có (không trùng, tăng dần) của bút toán gốc một chứng từ nguồn, cho mẫu in chứng từ. */
    public record SourceAccounts(List<String> debitCodes, List<String> creditCodes) {
    }

    public record JournalFilter(Long companyId, Long branchId, LocalDate dateFrom, LocalDate dateTo,
                                String sourceType, String keyword, Long accountId, JournalEntryStatus status) {
    }

    private final JournalEntryRepository journalRepository;
    private final AccountRepository accountRepository;
    private final AccountingAccess access;
    private final MasterDataQueryService masterData;

    public JournalQueryService(JournalEntryRepository journalRepository, AccountRepository accountRepository,
                               AccountingAccess access, MasterDataQueryService masterData) {
        this.journalRepository = journalRepository;
        this.accountRepository = accountRepository;
        this.access = access;
        this.masterData = masterData;
    }

    @Transactional(readOnly = true)
    public Page<JournalEntrySummaryResponse> search(CurrentUser actor, JournalFilter filter, Pageable pageable) {
        access.requirePermission(actor, VIEW);
        access.requireCompany(actor, filter.companyId());
        Set<Long> branches = actor.dataScope().hasFullCompanyAccess(filter.companyId()) ? null
                : actor.dataScope().branchIds();
        Specification<JournalEntry> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("companyId"), filter.companyId()));
            if (branches != null) {
                predicates.add(branches.isEmpty() ? cb.disjunction() : root.get("branchId").in(branches));
            }
            if (filter.branchId() != null) {
                predicates.add(cb.equal(root.get("branchId"), filter.branchId()));
            }
            if (filter.dateFrom() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("postingDate"), filter.dateFrom()));
            }
            if (filter.dateTo() != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("postingDate"), filter.dateTo()));
            }
            if (filter.sourceType() != null && !filter.sourceType().isBlank()) {
                predicates.add(cb.equal(root.get("sourceType"), filter.sourceType().trim()));
            }
            if (filter.keyword() != null && !filter.keyword().isBlank()) {
                String like = "%" + filter.keyword().trim().toLowerCase() + "%";
                predicates.add(cb.or(cb.like(cb.lower(root.get("entryNo")), like),
                        cb.like(cb.lower(root.get("sourceNo")), like)));
            }
            if (filter.status() != null) {
                predicates.add(cb.equal(root.get("status"), filter.status()));
            }
            if (filter.accountId() != null) {
                Subquery<Long> lines = query.subquery(Long.class);
                Root<JournalEntry> entry = lines.from(JournalEntry.class);
                Join<JournalEntry, JournalEntryLine> line = entry.join("lines");
                lines.select(entry.get("id")).where(cb.equal(line.get("accountId"), filter.accountId()));
                predicates.add(root.get("id").in(lines));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
        return journalRepository.findAll(spec, PageRequests.checked(pageable, SORTABLE,
                        Sort.by(Sort.Order.desc("postingDate"), Sort.Order.desc("id"))))
                .map(JournalQueryService::toSummary);
    }

    @Transactional(readOnly = true)
    public JournalEntryResponse get(CurrentUser actor, Long id) {
        access.requirePermission(actor, VIEW);
        JournalEntry entry = journalRepository.findById(id)
                .filter(found -> access.canAccessBranch(actor, found.getCompanyId(), found.getBranchId()))
                .orElseThrow(BusinessException::notFound);
        return toResponse(entry);
    }

    /** Bút toán (gốc và đảo) của một chứng từ nguồn, để trang chứng từ hiển thị tab "Bút toán". */
    @Transactional(readOnly = true)
    public List<JournalEntryResponse> bySource(CurrentUser actor, Long companyId, String sourceType, Long sourceId) {
        access.requirePermission(actor, VIEW);
        access.requireCompany(actor, companyId);
        return journalRepository.findAll((root, query, cb) -> cb.and(cb.equal(root.get("companyId"), companyId),
                        cb.equal(root.get("sourceType"), sourceType), cb.equal(root.get("sourceId"), sourceId)),
                        Sort.by("id")).stream()
                .filter(entry -> access.canAccessBranch(actor, entry.getCompanyId(), entry.getBranchId()))
                .map(this::toResponse)
                .toList();
    }

    /**
     * Tài khoản của bút toán gốc (không tính bút toán đảo) theo chứng từ nguồn, chỉ các dòng hạch toán của những dòng
     * chứng từ nguồn cho trước. API cho module khác in chứng từ: module gọi tự kiểm tra quyền xem chứng từ nguồn.
     */
    @Transactional(readOnly = true)
    public SourceAccounts accountsBySource(Long companyId, String sourceType, Long sourceId,
                                           Set<Long> sourceLineIds) {
        List<JournalEntryLine> lines = journalRepository.findAll((root, query, cb) -> cb.and(
                        cb.equal(root.get("companyId"), companyId), cb.equal(root.get("sourceType"), sourceType),
                        cb.equal(root.get("sourceId"), sourceId), cb.isNull(root.get("reversalOfId"))))
                .stream().flatMap(entry -> entry.getLines().stream())
                .filter(line -> sourceLineIds.contains(line.getSourceLineId()))
                .toList();
        Map<Long, String> codes = accountRepository.findByIdIn(lines.stream().map(JournalEntryLine::getAccountId)
                .collect(Collectors.toSet())).stream().collect(Collectors.toMap(Account::getId, Account::getCode));
        return new SourceAccounts(accountCodes(lines, codes, true), accountCodes(lines, codes, false));
    }

    /**
     * Tài khoản bút toán gốc theo từng dòng chứng từ nguồn của nhiều chứng từ cùng loại (sổ chi tiết, báo cáo: một truy
     * vấn cho cả danh sách thay vì gọi {@link #accountsBySource} từng chứng từ). Khóa của map là ID dòng chứng từ nguồn.
     * Module gọi tự kiểm tra quyền xem chứng từ nguồn.
     */
    @Transactional(readOnly = true)
    public Map<Long, SourceAccounts> accountsBySourceLines(Long companyId, String sourceType,
                                                            Collection<Long> sourceIds) {
        Map<Long, SortedSet<String>> debits = new HashMap<>();
        Map<Long, SortedSet<String>> credits = new HashMap<>();
        List<Long> ids = List.copyOf(new LinkedHashSet<>(sourceIds));
        for (int from = 0; from < ids.size(); from += SOURCE_BATCH) {
            List<Long> batch = ids.subList(from, Math.min(ids.size(), from + SOURCE_BATCH));
            for (Object[] row : journalRepository.findSourceLineAccounts(companyId, sourceType, batch)) {
                Long lineId = (Long) row[0];
                boolean debit = ((BigDecimal) row[2]).signum() > 0;
                (debit ? debits : credits).computeIfAbsent(lineId, key -> new TreeSet<>()).add((String) row[1]);
            }
        }
        Set<Long> lineIds = new HashSet<>(debits.keySet());
        lineIds.addAll(credits.keySet());
        Map<Long, SourceAccounts> result = new HashMap<>();
        lineIds.forEach(lineId -> result.put(lineId, new SourceAccounts(
                List.copyOf(debits.getOrDefault(lineId, new TreeSet<>())),
                List.copyOf(credits.getOrDefault(lineId, new TreeSet<>())))));
        return result;
    }

    private static List<String> accountCodes(List<JournalEntryLine> lines, Map<Long, String> codes, boolean debit) {
        return lines.stream()
                .filter(line -> (debit ? line.getDebitAmount() : line.getCreditAmount()).signum() > 0)
                .map(line -> codes.get(line.getAccountId())).distinct().sorted().toList();
    }

    private JournalEntryResponse toResponse(JournalEntry entry) {
        Map<Long, Account> accounts = accountRepository.findByIdIn(entry.getLines().stream()
                        .map(JournalEntryLine::getAccountId).collect(Collectors.toSet())).stream()
                .collect(Collectors.toMap(Account::getId, Function.identity()));
        Set<Long> partnerIds = entry.getLines().stream().map(JournalEntryLine::getPartnerId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, PartnerInfo> partners = partnerIds.isEmpty() ? Map.of()
                : masterData.partners(partnerIds).stream()
                .collect(Collectors.toMap(PartnerInfo::id, Function.identity()));
        List<JournalEntryLineResponse> lines = entry.getLines().stream().map(line -> {
            Account account = accounts.get(line.getAccountId());
            PartnerInfo partner = line.getPartnerId() == null ? null : partners.get(line.getPartnerId());
            return new JournalEntryLineResponse(line.getId(), line.getLineNo(), line.getAccountId(),
                    account == null ? null : account.getCode(), account == null ? null : account.getName(),
                    line.getDebitAmount(), line.getCreditAmount(), line.getTransactionCode(), line.getPostingRuleId(),
                    line.getPartnerId(), partner == null ? null : partner.code(),
                    partner == null ? null : partner.name(), line.getProductId(), line.getWarehouseId(), line.getDepartmentId(),
                    line.getEmployeeId(), line.getSourceLineId(), line.getDescription());
        }).toList();
        JournalEntrySummaryResponse reversedBy = entry.getStatus() == JournalEntryStatus.REVERSED
                ? journalRepository.findOne((root, query, cb) -> cb.equal(root.get("reversalOfId"), entry.getId()))
                .map(JournalQueryService::toSummary).orElse(null)
                : null;
        return new JournalEntryResponse(toSummary(entry), lines, reversedBy);
    }

    private static JournalEntrySummaryResponse toSummary(JournalEntry entry) {
        return new JournalEntrySummaryResponse(entry.getId(), entry.getCompanyId(), entry.getBranchId(),
                entry.getEntryNo(), entry.getPostingDate(), entry.getEntryKind(), entry.getSourceType(),
                entry.getSourceId(), entry.getSourceNo(), entry.getDescription(), entry.getTotalAmount(),
                entry.getStatus(), entry.getReversalOfId(), entry.getCreatedAt());
    }
}
