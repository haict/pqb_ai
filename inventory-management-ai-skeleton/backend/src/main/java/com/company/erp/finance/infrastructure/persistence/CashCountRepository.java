package com.company.erp.finance.infrastructure.persistence;

import com.company.erp.finance.domain.CashCount;
import com.company.erp.finance.domain.FinanceDocumentStatus;
import jakarta.persistence.LockModeType;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CashCountRepository extends JpaRepository<CashCount, Long>,
        JpaSpecificationExecutor<CashCount> {

    /** Khóa ghi chứng từ trước khi đổi trạng thái (BR-GEN-51). */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select d from CashCount d where d.id = :id")
    Optional<CashCount> findByIdForUpdate(@Param("id") Long id);

    /** Số chứng từ chờ duyệt / đã duyệt chưa ghi sổ trong kỳ của chi nhánh (điều kiện khóa kỳ). */
    @Query("""
            select d.documentNo from CashCount d
            where d.companyId = :companyId and d.branchId = :branchId and d.status in :statuses
              and d.postingDate between :startDate and :endDate
            order by d.documentNo
            """)
    List<String> findUnfinishedNumbers(@Param("companyId") Long companyId, @Param("branchId") Long branchId,
                                       @Param("statuses") Collection<FinanceDocumentStatus> statuses,
                                       @Param("startDate") LocalDate startDate, @Param("endDate") LocalDate endDate);
}
