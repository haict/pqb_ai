package com.company.erp.common.infrastructure.persistence;

import com.company.erp.common.domain.DocumentSequence;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DocumentSequenceRepository extends JpaRepository<DocumentSequence, Long> {

    boolean existsByCompanyIdAndBranchIdAndDocumentTypeAndPeriodKey(Long companyId, Long branchId,
                                                                    String documentType, String periodKey);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select s from DocumentSequence s
            where s.companyId = :companyId and s.branchId = :branchId
              and s.documentType = :documentType and s.periodKey = :periodKey
            """)
    Optional<DocumentSequence> findForUpdate(@Param("companyId") Long companyId,
                                             @Param("branchId") Long branchId,
                                             @Param("documentType") String documentType,
                                             @Param("periodKey") String periodKey);

    /** Tạo dòng bộ đếm nếu chưa có; bỏ qua khi dòng đã tồn tại (unique key). */
    @Modifying
    @Query(nativeQuery = true, value = """
            insert ignore into document_sequences
                (company_id, branch_id, document_type, period_key, next_value, version, created_at, updated_at)
            values (:companyId, :branchId, :documentType, :periodKey, 1, 0, :now, :now)
            """)
    int insertIfAbsent(@Param("companyId") Long companyId,
                       @Param("branchId") Long branchId,
                       @Param("documentType") String documentType,
                       @Param("periodKey") String periodKey,
                       @Param("now") Instant now);
}
