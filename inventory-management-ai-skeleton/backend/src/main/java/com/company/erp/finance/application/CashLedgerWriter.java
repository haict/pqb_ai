package com.company.erp.finance.application;

import com.company.erp.common.domain.PeriodModule;
import com.company.erp.finance.domain.CashAccount;
import com.company.erp.finance.domain.CashLedgerEntry;
import com.company.erp.finance.infrastructure.persistence.CashAccountRepository;
import com.company.erp.finance.infrastructure.persistence.CashLedgerEntryRepository;
import com.company.erp.system.application.PeriodGuard;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * Ghi sổ quỹ / sổ tiền gửi và cập nhật số dư quỹ trong transaction ghi sổ của chứng từ (BR-CSH-01, 03): khóa quỹ
 * theo id tăng dần, kiểm tra kỳ quỹ của chi nhánh sở hữu quỹ, quỹ tiền mặt không âm. Chỉ gọi trong transaction ghi
 * sổ của {@link CashDocumentPostingService}.
 */
@Component
class CashLedgerWriter {

    private final CashAccountRepository accountRepository;
    private final CashLedgerEntryRepository ledgerRepository;
    private final PeriodGuard periodGuard;

    CashLedgerWriter(CashAccountRepository accountRepository, CashLedgerEntryRepository ledgerRepository,
                     PeriodGuard periodGuard) {
        this.accountRepository = accountRepository;
        this.ledgerRepository = ledgerRepository;
        this.periodGuard = periodGuard;
    }

    /** Khóa ghi các quỹ theo id tăng dần (thứ tự khóa cố định: chứng từ → quỹ → khoản công nợ, docs/02 mục 6). */
    Map<Long, CashAccount> lock(Collection<Long> ids) {
        return accountRepository.findAllForUpdate(new TreeSet<>(ids)).stream()
                .collect(Collectors.toMap(CashAccount::getId, Function.identity()));
    }

    /** Một dòng tiền vào / ra của quỹ đã khóa. */
    void record(CashAccount account, LocalDate postingDate, String transactionCode, boolean inflow,
                BigDecimal amount, CashLedgerEntry.Reference reference, String description, Long actorId,
                Instant now) {
        periodGuard.assertOpen(account.getCompanyId(), account.getBranchId(), PeriodModule.CASH, postingDate);
        account.apply(inflow ? amount : amount.negate());
        accountRepository.save(account);
        ledgerRepository.save(CashLedgerEntry.of(account, postingDate, transactionCode, inflow, amount, reference,
                description, actorId, now));
    }

    /** Dòng ngược đúng từng dòng gốc của chứng từ (BR-GEN-21); quỹ tiền mặt không âm sau khi đảo. */
    void reverse(Long companyId, String referenceType, Long referenceId, Map<Long, CashAccount> accounts,
                 LocalDate reversalDate, Long actorId, Instant now) {
        List<CashLedgerEntry> originals = ledgerRepository.findOriginals(companyId, referenceType, referenceId);
        if (originals.isEmpty()) {
            throw new IllegalStateException("Chứng từ " + referenceType + "#" + referenceId + " chưa ghi sổ quỹ");
        }
        for (CashLedgerEntry original : originals) {
            CashAccount account = accounts.get(original.getCashAccountId());
            periodGuard.assertOpen(account.getCompanyId(), account.getBranchId(), PeriodModule.CASH, reversalDate);
            CashLedgerEntry reversal = original.reversed(reversalDate, actorId, now);
            account.apply(reversal.netAmount());
            accountRepository.save(account);
            ledgerRepository.save(reversal);
        }
    }
}
