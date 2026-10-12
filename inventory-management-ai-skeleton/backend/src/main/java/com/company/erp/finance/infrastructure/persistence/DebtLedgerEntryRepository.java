package com.company.erp.finance.infrastructure.persistence;

import com.company.erp.finance.domain.DebtLedgerEntry;
import com.company.erp.finance.domain.DebtType;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DebtLedgerEntryRepository extends JpaRepository<DebtLedgerEntry, Long> {

    /** Dòng sổ gốc (không phải dòng đảo) của các khoản công nợ, để sinh dòng ngược khi đảo. */
    @Query("""
            select e from DebtLedgerEntry e
            where e.debtItemId in :itemIds and e.reversalOfId is null
              and e.referenceType = :referenceType and e.referenceId = :referenceId
            order by e.id
            """)
    List<DebtLedgerEntry> findOriginals(@Param("itemIds") Collection<Long> itemIds,
                                        @Param("referenceType") String referenceType,
                                        @Param("referenceId") Long referenceId);

    /** Σ Nợ − Σ Có của đối tượng trước ngày {@code before} trong các chi nhánh (null = mọi chi nhánh). */
    @Query("""
            select coalesce(sum(e.debitAmount - e.creditAmount), 0) from DebtLedgerEntry e
            where e.companyId = :companyId and e.debtType = :debtType and e.partnerId = :partnerId
              and e.postingDate < :before and (:allBranches = true or e.branchId in :branchIds)
            """)
    BigDecimal netBefore(@Param("companyId") Long companyId, @Param("debtType") DebtType debtType,
                         @Param("partnerId") Long partnerId, @Param("before") LocalDate before,
                         @Param("allBranches") boolean allBranches, @Param("branchIds") Collection<Long> branchIds);

    @Query("""
            select e from DebtLedgerEntry e
            where e.companyId = :companyId and e.debtType = :debtType and e.partnerId = :partnerId
              and e.postingDate between :from and :to and (:allBranches = true or e.branchId in :branchIds)
            order by e.postingDate, e.id
            """)
    List<DebtLedgerEntry> findInRange(@Param("companyId") Long companyId, @Param("debtType") DebtType debtType,
                                      @Param("partnerId") Long partnerId, @Param("from") LocalDate from,
                                      @Param("to") LocalDate to, @Param("allBranches") boolean allBranches,
                                      @Param("branchIds") Collection<Long> branchIds);

    /** Dòng sổ gốc (không phải dòng đảo) do một chứng từ nguồn sinh ra, để sinh dòng ngược khi đảo. */
    @Query("""
            select e from DebtLedgerEntry e
            where e.companyId = :companyId and e.referenceType = :referenceType and e.referenceId = :referenceId
              and e.reversalOfId is null
            order by e.id
            """)
    List<DebtLedgerEntry> findOriginalsByReference(@Param("companyId") Long companyId,
                                                   @Param("referenceType") String referenceType,
                                                   @Param("referenceId") Long referenceId);
}
