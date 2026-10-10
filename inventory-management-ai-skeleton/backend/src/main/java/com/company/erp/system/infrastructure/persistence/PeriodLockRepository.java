package com.company.erp.system.infrastructure.persistence;

import com.company.erp.common.domain.PeriodModule;
import com.company.erp.system.domain.PeriodLock;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PeriodLockRepository extends JpaRepository<PeriodLock, Long> {

    Optional<PeriodLock> findByFiscalPeriodIdAndBranchIdAndModule(Long fiscalPeriodId, Long branchId,
                                                                  PeriodModule module);

    List<PeriodLock> findByFiscalPeriodIdInAndBranchId(Collection<Long> fiscalPeriodIds, Long branchId);

    /** Khóa ghi dòng trạng thái để đổi khóa/mở. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select l from PeriodLock l
            where l.fiscalPeriodId = :periodId and l.branchId = :branchId and l.module = :module
            """)
    Optional<PeriodLock> findForUpdate(@Param("periodId") Long fiscalPeriodId, @Param("branchId") Long branchId,
                                       @Param("module") PeriodModule module);

    /** Đọc khóa (FOR SHARE) để thấy trạng thái đã commit mới nhất, không phải ảnh chụp đầu transaction. */
    @Lock(LockModeType.PESSIMISTIC_READ)
    @Query("""
            select l from PeriodLock l
            where l.fiscalPeriodId = :periodId and l.branchId = :branchId and l.module = :module
            """)
    Optional<PeriodLock> findForShare(@Param("periodId") Long fiscalPeriodId, @Param("branchId") Long branchId,
                                      @Param("module") PeriodModule module);

    /** Tạo dòng trạng thái (đang mở) nếu chưa có; bỏ qua khi đã tồn tại (unique key). */
    @Modifying
    @Query(nativeQuery = true, value = """
            insert ignore into period_locks
                (fiscal_period_id, branch_id, module, locked, created_at, updated_at, version)
            values (:periodId, :branchId, :module, false, :now, :now, 0)
            """)
    int insertIfAbsent(@Param("periodId") Long fiscalPeriodId, @Param("branchId") Long branchId,
                       @Param("module") String module, @Param("now") Instant now);
}
