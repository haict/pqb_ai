package com.company.erp.accounting.infrastructure.persistence;

import com.company.erp.accounting.domain.JournalEntry;
import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface JournalEntryRepository extends JpaRepository<JournalEntry, Long>,
        JpaSpecificationExecutor<JournalEntry> {

    /** Bút toán gốc (không phải bút toán đảo) của chứng từ nguồn, khóa ghi trước khi đảo. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select e from JournalEntry e
            where e.companyId = :companyId and e.sourceType = :sourceType and e.sourceId = :sourceId
              and e.reversalOfId is null
            """)
    Optional<JournalEntry> findOriginalForUpdate(@Param("companyId") Long companyId,
                                                 @Param("sourceType") String sourceType,
                                                 @Param("sourceId") Long sourceId);

    @Query("""
            select count(e) > 0 from JournalEntry e
            where e.companyId = :companyId and e.sourceType = :sourceType and e.sourceId = :sourceId
              and e.reversalOfId is null
            """)
    boolean existsOriginal(@Param("companyId") Long companyId, @Param("sourceType") String sourceType,
                           @Param("sourceId") Long sourceId);

    /**
     * Dòng hạch toán theo dòng chứng từ nguồn của bút toán gốc (không tính bút toán đảo) nhiều chứng từ: mỗi phần tử là
     * {@code [sourceLineId, mã tài khoản, số tiền Nợ]}.
     */
    @Query("""
            select l.sourceLineId, a.code, l.debitAmount from JournalEntry e join e.lines l, Account a
            where a.id = l.accountId and e.companyId = :companyId and e.sourceType = :sourceType
              and e.sourceId in :sourceIds and e.reversalOfId is null and l.sourceLineId is not null
            """)
    List<Object[]> findSourceLineAccounts(@Param("companyId") Long companyId, @Param("sourceType") String sourceType,
                                          @Param("sourceIds") Collection<Long> sourceIds);

    @Query("select count(l) > 0 from JournalEntry e join e.lines l where l.accountId = :accountId")
    boolean existsLineForAccount(@Param("accountId") Long accountId);
}
