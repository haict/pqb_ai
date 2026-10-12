package com.company.erp.sales.infrastructure.persistence;

import com.company.erp.sales.domain.SalesQuote;
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

public interface SalesQuoteRepository extends JpaRepository<SalesQuote, Long>, JpaSpecificationExecutor<SalesQuote> {

    /** Khóa ghi chứng từ trước khi đổi trạng thái (BR-GEN-51). */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select d from SalesQuote d where d.id = :id")
    Optional<SalesQuote> findByIdForUpdate(@Param("id") Long id);

    /** Id chứng từ chứa các dòng, tăng dần (khóa từng chứng từ theo thứ tự này). */
    @Query("select distinct d.id from SalesQuote d join d.lines l where l.id in :lineIds order by d.id")
    List<Long> findIdsByLineIds(@Param("lineIds") Collection<Long> lineIds);

    @Query("select distinct d from SalesQuote d join d.lines l where l.id in :lineIds")
    List<SalesQuote> findByLineIds(@Param("lineIds") Collection<Long> lineIds);

    /** Báo giá đã duyệt / đã gửi quá hạn hiệu lực (tác vụ hết hạn, BR-SAL-05). */
    @Query("""
            select d.id from SalesQuote d
            where d.status in (com.company.erp.sales.domain.SalesDocumentStatus.APPROVED,
                               com.company.erp.sales.domain.SalesDocumentStatus.SENT)
              and d.validUntil < :today
            """)
    List<Long> findExpiredIds(@Param("today") LocalDate today);
}
