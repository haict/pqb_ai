package com.company.erp.system.infrastructure.persistence;

import com.company.erp.system.domain.FiscalPeriod;
import jakarta.persistence.LockModeType;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FiscalPeriodRepository extends JpaRepository<FiscalPeriod, Long> {

    List<FiscalPeriod> findByCompanyIdAndYearOrderByMonth(Long companyId, int year);

    Optional<FiscalPeriod> findByCompanyIdAndYearAndMonth(Long companyId, int year, int month);

    /**
     * Khóa ghi các kỳ theo thứ tự id. Khóa/mở kỳ khóa cả kỳ liền kề để kiểm tra thứ tự kỳ không bị
     * thao tác đồng thời trên kỳ liền kề làm sai (BR-SYS-31).
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from FiscalPeriod p where p.id in :ids order by p.id")
    List<FiscalPeriod> lockAllById(@Param("ids") Collection<Long> ids);

    /**
     * Kỳ chứa ngày hạch toán, khóa đọc (FOR SHARE): khóa kỳ phải chờ giao dịch ghi sổ đang kiểm tra kỳ
     * kết thúc, nên không có chứng từ lọt vào kỳ vừa khóa.
     */
    @Lock(LockModeType.PESSIMISTIC_READ)
    @Query("""
            select p from FiscalPeriod p
            where p.companyId = :companyId and p.startDate <= :date and p.endDate >= :date
            """)
    Optional<FiscalPeriod> findCoveringForShare(@Param("companyId") Long companyId, @Param("date") LocalDate date);
}
