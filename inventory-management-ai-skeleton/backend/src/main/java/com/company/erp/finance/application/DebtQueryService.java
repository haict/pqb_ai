package com.company.erp.finance.application;

import com.company.erp.common.api.PageRequests;
import com.company.erp.common.api.PageResponse;
import com.company.erp.common.application.BusinessCalendar;
import com.company.erp.common.domain.BusinessException;
import com.company.erp.finance.application.dto.AgingResponse;
import com.company.erp.finance.application.dto.AgingRowResponse;
import com.company.erp.finance.application.dto.DebtItemResponse;
import com.company.erp.finance.application.dto.DebtLedgerLineResponse;
import com.company.erp.finance.application.dto.DebtLedgerResponse;
import com.company.erp.finance.application.dto.DebtPrepaymentResponse;
import com.company.erp.finance.domain.DebtItem;
import com.company.erp.finance.domain.DebtItemStatus;
import com.company.erp.finance.domain.DebtLedgerEntry;
import com.company.erp.finance.domain.DebtType;
import com.company.erp.finance.infrastructure.persistence.DebtAgingJdbc;
import com.company.erp.finance.infrastructure.persistence.DebtAgingJdbc.AgingFilter;
import com.company.erp.finance.infrastructure.persistence.DebtAgingJdbc.AgingRow;
import com.company.erp.finance.infrastructure.persistence.DebtItemRepository;
import com.company.erp.finance.infrastructure.persistence.DebtLedgerEntryRepository;
import com.company.erp.finance.infrastructure.persistence.DebtPrepaymentRepository;
import com.company.erp.masterdata.application.MasterDataQueryService;
import com.company.erp.masterdata.application.MasterDataQueryService.PartnerInfo;
import com.company.erp.system.application.CurrentUser;
import com.company.erp.system.application.ParameterService;
import jakarta.persistence.criteria.Predicate;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Tra cứu công nợ (chỉ đọc, quyền {@code debt:view}): khoản công nợ theo hóa đơn, tuổi nợ (BR-AR-05), sổ chi tiết
 * công nợ theo đối tượng. Lọc theo phạm vi chi nhánh; số liệu tính từ khoản công nợ / sổ chi tiết (BR-RPT-11).
 */
@Service
public class DebtQueryService {

    static final String VIEW = "debt:view";
    private static final Set<String> SORTABLE = Set.of("dueDate", "documentDate", "originalAmount", "createdAt");
    private static final List<String> BUCKET_KEYS = List.of("finance.aging-bucket-1-days",
            "finance.aging-bucket-2-days", "finance.aging-bucket-3-days");

    public record DebtItemFilter(Long companyId, DebtType debtType, Long branchId, Long partnerId, String keyword,
                                 Boolean openOnly, Boolean overdueOnly) {
    }

    private final DebtItemRepository itemRepository;
    private final DebtLedgerEntryRepository ledgerRepository;
    private final DebtAgingJdbc agingJdbc;
    private final DebtPrepaymentRepository prepaymentRepository;
    private final MasterDataQueryService masterData;
    private final ParameterService parameters;
    private final FinanceAccess access;
    private final BusinessCalendar calendar;

    public DebtQueryService(DebtItemRepository itemRepository, DebtLedgerEntryRepository ledgerRepository,
                            DebtAgingJdbc agingJdbc, DebtPrepaymentRepository prepaymentRepository,
                            MasterDataQueryService masterData, ParameterService parameters, FinanceAccess access,
                            BusinessCalendar calendar) {
        this.itemRepository = itemRepository;
        this.ledgerRepository = ledgerRepository;
        this.agingJdbc = agingJdbc;
        this.prepaymentRepository = prepaymentRepository;
        this.masterData = masterData;
        this.parameters = parameters;
        this.access = access;
        this.calendar = calendar;
    }

    @Transactional(readOnly = true)
    public Page<DebtItemResponse> items(CurrentUser actor, DebtItemFilter filter, Pageable pageable) {
        access.requirePermission(actor, VIEW);
        Set<Long> branches = access.branchScope(actor, filter.companyId());
        LocalDate today = calendar.today();
        Specification<DebtItem> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("companyId"), filter.companyId()));
            predicates.add(cb.equal(root.get("debtType"), filter.debtType()));
            if (branches != null) {
                predicates.add(root.get("branchId").in(branches));
            }
            if (filter.branchId() != null) {
                predicates.add(cb.equal(root.get("branchId"), filter.branchId()));
            }
            if (filter.partnerId() != null) {
                predicates.add(cb.equal(root.get("partnerId"), filter.partnerId()));
            }
            if (filter.keyword() != null && !filter.keyword().isBlank()) {
                String like = "%" + filter.keyword().trim().toLowerCase() + "%";
                predicates.add(cb.or(cb.like(cb.lower(root.get("invoiceNo")), like),
                        cb.like(cb.lower(root.get("sourceNo")), like)));
            }
            if (Boolean.TRUE.equals(filter.openOnly()) || Boolean.TRUE.equals(filter.overdueOnly())) {
                predicates.add(root.get("status").in(EnumSet.of(DebtItemStatus.OPEN, DebtItemStatus.PARTIAL)));
            }
            if (Boolean.TRUE.equals(filter.overdueOnly())) {
                predicates.add(cb.lessThan(root.get("dueDate"), today));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
        Page<DebtItem> page = itemRepository.findAll(spec, PageRequests.checked(pageable, SORTABLE,
                Sort.by(Sort.Order.asc("dueDate"), Sort.Order.asc("id"))));
        Map<Long, PartnerInfo> partners = partners(page.getContent().stream().map(DebtItem::getPartnerId).toList());
        return page.map(item -> toResponse(item, partners.get(item.getPartnerId()), today));
    }

    @Transactional(readOnly = true)
    public AgingResponse aging(CurrentUser actor, Long companyId, DebtType debtType, Long branchId, Long partnerId,
                               LocalDate asOf, Pageable pageable) {
        access.requirePermission(actor, VIEW);
        Set<Long> branches = access.branchScope(actor, companyId);
        List<Integer> buckets = BUCKET_KEYS.stream().map(key -> parameters.integerValue(companyId, key)).toList();
        if (buckets.get(0) >= buckets.get(1) || buckets.get(1) >= buckets.get(2)) {
            throw BusinessException.invalidField("asOf", "Tham số mốc tuổi nợ phải tăng dần (mốc 1 < mốc 2 < mốc 3).");
        }
        Set<Long> branchFilter = branchId == null ? branches
                : (branches == null || branches.contains(branchId) ? Set.of(branchId) : Set.of(-1L));
        AgingFilter filter = new AgingFilter(companyId, debtType, asOf != null ? asOf : calendar.today(),
                branchFilter, partnerId, buckets.get(0), buckets.get(1), buckets.get(2));
        Pageable page = PageRequests.checked(pageable, Set.of(), Sort.unsorted());
        List<AgingRow> rows = agingJdbc.rows(filter, page.getPageSize(), page.getOffset());
        Map<Long, PartnerInfo> partners = partners(rows.stream().map(AgingRow::partnerId).toList());
        List<AgingRowResponse> mapped = rows.stream().map(row -> toAging(row, partners.get(row.partnerId()))).toList();
        PageResponse<AgingRowResponse> pageResponse = PageResponse.from(
                new PageImpl<>(mapped, page, agingJdbc.countPartners(filter)), Function.identity());
        return new AgingResponse(filter.asOf(), buckets, pageResponse, toAging(agingJdbc.totals(filter), null));
    }

    /** Sổ chi tiết công nợ của một đối tượng: số dư đầu kỳ, từng dòng phát sinh và số dư lũy kế, số dư cuối. */
    @Transactional(readOnly = true)
    public DebtLedgerResponse ledger(CurrentUser actor, Long companyId, DebtType debtType, Long partnerId,
                                     LocalDate dateFrom, LocalDate dateTo) {
        access.requirePermission(actor, VIEW);
        Set<Long> branches = access.branchScope(actor, companyId);
        if (dateTo.isBefore(dateFrom)) {
            throw BusinessException.invalidField("dateTo", "Đến ngày phải từ ngày bắt đầu trở đi.");
        }
        PartnerInfo partner = masterData.partner(partnerId)
                .filter(found -> found.companyId().equals(companyId))
                .orElseThrow(BusinessException::notFound);
        boolean all = branches == null;
        Set<Long> branchIds = all ? Set.of(-1L) : branches;
        int sign = debtType.increasesOnDebit() ? 1 : -1;
        BigDecimal balance = ledgerRepository.netBefore(companyId, debtType, partnerId, dateFrom, all, branchIds)
                .multiply(BigDecimal.valueOf(sign));
        BigDecimal opening = balance;
        BigDecimal totalDebit = BigDecimal.ZERO;
        BigDecimal totalCredit = BigDecimal.ZERO;
        List<DebtLedgerLineResponse> lines = new ArrayList<>();
        for (DebtLedgerEntry entry : ledgerRepository.findInRange(companyId, debtType, partnerId, dateFrom, dateTo,
                all, branchIds)) {
            totalDebit = totalDebit.add(entry.getDebitAmount());
            totalCredit = totalCredit.add(entry.getCreditAmount());
            balance = balance.add(entry.getDebitAmount().subtract(entry.getCreditAmount())
                    .multiply(BigDecimal.valueOf(sign)));
            lines.add(new DebtLedgerLineResponse(entry.getId(), entry.getPostingDate(), entry.getEntryType(),
                    entry.getReferenceType(), entry.getReferenceId(), entry.getReferenceNo(), entry.getDebtItemId(),
                    entry.getDescription(), entry.getDebitAmount(), entry.getCreditAmount(), balance));
        }
        return new DebtLedgerResponse(debtType, partnerId, partner.code(), partner.name(), dateFrom, dateTo, opening,
                totalDebit, totalCredit, balance, lines);
    }

    /** Tiền trả trước còn chưa bù trừ của đối tượng theo chi nhánh trong phạm vi (BR-AR-04). */
    @Transactional(readOnly = true)
    public List<DebtPrepaymentResponse> prepayments(CurrentUser actor, Long companyId, DebtType debtType,
                                                    Long partnerId) {
        access.requirePermission(actor, VIEW);
        Set<Long> branches = access.branchScope(actor, companyId);
        return prepaymentRepository.findByCompanyIdAndDebtTypeAndPartnerId(companyId, debtType, partnerId).stream()
                .filter(row -> branches == null || branches.contains(row.getBranchId()))
                .filter(row -> row.getAmount().signum() > 0)
                .map(row -> new DebtPrepaymentResponse(row.getBranchId(), row.getDebtType(), row.getPartnerId(),
                        row.getAmount()))
                .toList();
    }

    private Map<Long, PartnerInfo> partners(List<Long> ids) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        return masterData.partners(Set.copyOf(ids)).stream()
                .collect(Collectors.toMap(PartnerInfo::id, Function.identity()));
    }

    static DebtItemResponse toResponse(DebtItem item, PartnerInfo partner, LocalDate today) {
        boolean open = item.getStatus() == DebtItemStatus.OPEN || item.getStatus() == DebtItemStatus.PARTIAL;
        return new DebtItemResponse(item.getId(), item.getBranchId(), item.getDebtType(), item.getPartnerId(),
                partner == null ? null : partner.code(), partner == null ? null : partner.name(),
                item.getSourceType(), item.getSourceId(), item.getSourceNo(), item.getInvoiceNo(),
                item.getDocumentDate(), item.getDueDate(), item.getOriginalAmount(), item.getSettledAmount(),
                item.getRemainingAmount(), open ? item.overdueDays(today) : 0, item.getStatus());
    }

    private static AgingRowResponse toAging(AgingRow row, PartnerInfo partner) {
        return new AgingRowResponse(row.partnerId(), partner == null ? null : partner.code(),
                partner == null ? null : partner.name(), row.notDue(), row.bucket1(), row.bucket2(), row.bucket3(),
                row.bucket4(), row.total());
    }
}
