package com.company.erp.finance.infrastructure.persistence;

import com.company.erp.finance.domain.CashLedgerEntry;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CashLedgerEntryRepository extends JpaRepository<CashLedgerEntry, Long> {

    interface BalanceRow {
        Long getCashAccountId();

        BigDecimal getBalance();
    }

    /** Tổng tiền vào, ra theo quỹ và mã nghiệp vụ. */
    interface FlowRow {
        Long getCashAccountId();

        String getTransactionCode();

        BigDecimal getInflow();

        BigDecimal getOutflow();
    }

    /** Dòng sổ gốc (không phải dòng đảo) do một chứng từ sinh ra, để sinh dòng ngược khi đảo. */
    @Query("""
            select e from CashLedgerEntry e
            where e.companyId = :companyId and e.referenceType = :referenceType and e.referenceId = :referenceId
              and e.reversalOfId is null
            order by e.id
            """)
    List<CashLedgerEntry> findOriginals(@Param("companyId") Long companyId,
                                        @Param("referenceType") String referenceType,
                                        @Param("referenceId") Long referenceId);

    @Query("""
            select coalesce(sum(e.debitAmount - e.creditAmount), 0) from CashLedgerEntry e
            where e.cashAccountId = :cashAccountId and e.postingDate < :before
            """)
    BigDecimal netBefore(@Param("cashAccountId") Long cashAccountId, @Param("before") LocalDate before);

    @Query("""
            select e from CashLedgerEntry e
            where e.cashAccountId = :cashAccountId and e.postingDate between :from and :to
            order by e.postingDate, e.id
            """)
    List<CashLedgerEntry> findInRange(@Param("cashAccountId") Long cashAccountId, @Param("from") LocalDate from,
                                      @Param("to") LocalDate to);

    /** Số dư đầu kỳ của nhiều quỹ: Σ Nợ − Σ Có trước ngày {@code before}. */
    @Query("""
            select e.cashAccountId as cashAccountId, sum(e.debitAmount - e.creditAmount) as balance
            from CashLedgerEntry e
            where e.cashAccountId in :cashAccountIds and e.postingDate < :before
            group by e.cashAccountId
            """)
    List<BalanceRow> openingBalances(@Param("cashAccountIds") Collection<Long> cashAccountIds,
                                  @Param("before") LocalDate before);

    @Query("""
            select e.cashAccountId as cashAccountId, e.transactionCode as transactionCode,
                   sum(e.debitAmount) as inflow, sum(e.creditAmount) as outflow
            from CashLedgerEntry e
            where e.cashAccountId in :cashAccountIds and e.postingDate between :from and :to
            group by e.cashAccountId, e.transactionCode
            """)
    List<FlowRow> flows(@Param("cashAccountIds") Collection<Long> cashAccountIds, @Param("from") LocalDate from,
                        @Param("to") LocalDate to);
}
