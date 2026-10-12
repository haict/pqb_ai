package com.company.erp.finance.application;

import com.company.erp.common.domain.BusinessException;
import com.company.erp.finance.application.dto.CashBookLineResponse;
import com.company.erp.finance.application.dto.CashBookResponse;
import com.company.erp.finance.application.dto.CashFlowResponse;
import com.company.erp.finance.application.dto.CashFlowResponse.AccountRow;
import com.company.erp.finance.application.dto.CashFlowResponse.PurposeRow;
import com.company.erp.finance.domain.CashAccount;
import com.company.erp.finance.domain.CashLedgerEntry;
import com.company.erp.finance.infrastructure.persistence.CashAccountRepository;
import com.company.erp.finance.infrastructure.persistence.CashLedgerEntryRepository;
import com.company.erp.finance.infrastructure.persistence.CashLedgerEntryRepository.BalanceRow;
import com.company.erp.finance.infrastructure.persistence.CashLedgerEntryRepository.FlowRow;
import com.company.erp.masterdata.application.MasterDataQueryService;
import com.company.erp.masterdata.application.MasterDataQueryService.PartnerInfo;
import com.company.erp.system.application.CurrentUser;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Sổ quỹ / sổ tiền gửi và dòng tiền thực tế (BR-CSH-01, BR-CSH-06 phần thực tế; quyền {@code cash-book:view}). Số liệu
 * tính từ sổ quỹ, lọc theo phạm vi chi nhánh; mỗi dòng có chứng từ nguồn để truy ngược (BR-RPT-11).
 */
@Service
public class CashBookQueryService {

    static final String VIEW = "cash-book:view";
    private static final long MAX_DAYS = 366;

    private final CashAccountRepository accountRepository;
    private final CashLedgerEntryRepository ledgerRepository;
    private final MasterDataQueryService masterData;
    private final FinanceAccess access;

    public CashBookQueryService(CashAccountRepository accountRepository, CashLedgerEntryRepository ledgerRepository,
                                MasterDataQueryService masterData, FinanceAccess access) {
        this.accountRepository = accountRepository;
        this.ledgerRepository = ledgerRepository;
        this.masterData = masterData;
        this.access = access;
    }

    @Transactional(readOnly = true)
    public CashBookResponse cashBook(CurrentUser actor, Long cashAccountId, LocalDate from, LocalDate to) {
        access.requirePermission(actor, VIEW);
        requireRange(from, to);
        CashAccount account = accountRepository.findById(cashAccountId)
                .filter(found -> access.canAccess(actor, found.getCompanyId(), found.getBranchId()))
                .orElseThrow(BusinessException::notFound);
        BigDecimal opening = ledgerRepository.netBefore(cashAccountId, from);
        List<CashLedgerEntry> entries = ledgerRepository.findInRange(cashAccountId, from, to);
        Map<Long, PartnerInfo> partners = partners(entries);
        BigDecimal balance = opening;
        BigDecimal totalIn = BigDecimal.ZERO;
        BigDecimal totalOut = BigDecimal.ZERO;
        List<CashBookLineResponse> lines = new ArrayList<>();
        for (CashLedgerEntry entry : entries) {
            balance = balance.add(entry.netAmount());
            totalIn = totalIn.add(entry.getDebitAmount());
            totalOut = totalOut.add(entry.getCreditAmount());
            PartnerInfo partner = entry.getPartnerId() == null ? null : partners.get(entry.getPartnerId());
            lines.add(new CashBookLineResponse(entry.getId(), entry.getPostingDate(), entry.getReferenceType(),
                    entry.getReferenceId(), entry.getReferenceNo(), entry.getTransactionCode(), entry.getPartnerId(),
                    partner == null ? null : partner.name(), entry.getCounterpartyName(), entry.getDescription(),
                    entry.getReversalOfId() != null, entry.getDebitAmount(), entry.getCreditAmount(), balance));
        }
        return new CashBookResponse(account.getId(), account.getCode(), account.getName(), from, to, opening,
                totalIn, totalOut, balance, lines);
    }

    /** Dòng tiền thực tế theo quỹ và theo nội dung thu chi; {@code branchId = null} = mọi chi nhánh trong phạm vi. */
    @Transactional(readOnly = true)
    public CashFlowResponse cashFlow(CurrentUser actor, Long companyId, Long branchId, LocalDate from,
                                     LocalDate to) {
        access.requirePermission(actor, VIEW);
        requireRange(from, to);
        Set<Long> branches = access.branchScope(actor, companyId);
        List<CashAccount> accounts = accountRepository.findByCompanyIdOrderByCode(companyId).stream()
                .filter(account -> branches == null || branches.contains(account.getBranchId()))
                .filter(account -> branchId == null || account.getBranchId().equals(branchId))
                .toList();
        if (accounts.isEmpty()) {
            return new CashFlowResponse(from, to, List.of(), List.of(), BigDecimal.ZERO, BigDecimal.ZERO,
                    BigDecimal.ZERO, BigDecimal.ZERO);
        }
        Set<Long> ids = accounts.stream().map(CashAccount::getId).collect(Collectors.toSet());
        Map<Long, BigDecimal> openings = ledgerRepository.openingBalances(ids, from).stream()
                .collect(Collectors.toMap(BalanceRow::getCashAccountId, BalanceRow::getBalance));
        List<FlowRow> flows = ledgerRepository.flows(ids, from, to);
        List<AccountRow> rows = accounts.stream().map(account -> {
            BigDecimal opening = openings.getOrDefault(account.getId(), BigDecimal.ZERO);
            BigDecimal in = sum(flows, account.getId(), FlowRow::getInflow);
            BigDecimal out = sum(flows, account.getId(), FlowRow::getOutflow);
            return new AccountRow(account.getId(), account.getCode(), account.getName(), account.getAccountType(),
                    account.getBranchId(), opening, in, out, opening.add(in).subtract(out));
        }).toList();
        Map<String, BigDecimal[]> byCode = new TreeMap<>();
        for (FlowRow flow : flows) {
            BigDecimal[] totals = byCode.computeIfAbsent(flow.getTransactionCode(),
                    code -> new BigDecimal[] {BigDecimal.ZERO, BigDecimal.ZERO});
            totals[0] = totals[0].add(flow.getInflow());
            totals[1] = totals[1].add(flow.getOutflow());
        }
        List<PurposeRow> purposes = byCode.entrySet().stream()
                .map(entry -> new PurposeRow(entry.getKey(), entry.getValue()[0], entry.getValue()[1])).toList();
        return new CashFlowResponse(from, to, rows, purposes, total(rows, AccountRow::openingBalance),
                total(rows, AccountRow::inAmount), total(rows, AccountRow::outAmount),
                total(rows, AccountRow::closingBalance));
    }

    private static void requireRange(LocalDate from, LocalDate to) {
        if (to.isBefore(from)) {
            throw BusinessException.invalidField("dateTo", "Đến ngày phải từ ngày bắt đầu trở đi.");
        }
        if (ChronoUnit.DAYS.between(from, to) >= MAX_DAYS) {
            throw BusinessException.invalidField("dateTo", "Khoảng ngày tối đa " + MAX_DAYS + " ngày.");
        }
    }

    private Map<Long, PartnerInfo> partners(List<CashLedgerEntry> entries) {
        Set<Long> ids = entries.stream().map(CashLedgerEntry::getPartnerId).filter(Objects::nonNull)
                .collect(Collectors.toSet());
        return ids.isEmpty() ? Map.of() : masterData.partners(ids).stream()
                .collect(Collectors.toMap(PartnerInfo::id, Function.identity()));
    }

    private static BigDecimal sum(List<FlowRow> flows, Long accountId, Function<FlowRow, BigDecimal> field) {
        return flows.stream().filter(flow -> flow.getCashAccountId().equals(accountId)).map(field)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static BigDecimal total(List<AccountRow> rows, Function<AccountRow, BigDecimal> field) {
        return rows.stream().map(field).reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
