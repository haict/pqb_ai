package com.company.erp.finance.infrastructure.persistence;

import com.company.erp.finance.domain.DebtItem;
import com.company.erp.finance.domain.DebtType;
import jakarta.persistence.LockModeType;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DebtItemRepository extends JpaRepository<DebtItem, Long>, JpaSpecificationExecutor<DebtItem> {

    /** Khoản công nợ của một chứng từ nguồn (không khóa; số còn lại kiểm tra lại sau khi khóa lúc phân bổ). */
    @Query("""
            select i from DebtItem i
            where i.companyId = :companyId and i.sourceType = :sourceType and i.sourceId = :sourceId
            order by i.id
            """)
    List<DebtItem> findBySource(@Param("companyId") Long companyId, @Param("sourceType") String sourceType,
                                @Param("sourceId") Long sourceId);

    @Query("""
            select i from DebtItem i
            where i.companyId = :companyId and i.sourceType = :sourceType and i.sourceId in :sourceIds
            """)
    List<DebtItem> findBySources(@Param("companyId") Long companyId, @Param("sourceType") String sourceType,
                                 @Param("sourceIds") Collection<Long> sourceIds);

    /** Khoản công nợ của một chứng từ nguồn, khóa ghi theo id tăng dần (thứ tự khóa cố định, docs/02 mục 6). */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select i from DebtItem i
            where i.companyId = :companyId and i.sourceType = :sourceType and i.sourceId = :sourceId
            order by i.id
            """)
    List<DebtItem> findBySourceForUpdate(@Param("companyId") Long companyId, @Param("sourceType") String sourceType,
                                         @Param("sourceId") Long sourceId);

    /** Hóa đơn (đối tượng, số hóa đơn) đã có khoản công nợ chưa đảo. */
    @Query("""
            select count(i) > 0 from DebtItem i
            where i.companyId = :companyId and i.debtType = :debtType and i.partnerId = :partnerId
              and i.invoiceNo = :invoiceNo and i.status <> com.company.erp.finance.domain.DebtItemStatus.REVERSED
            """)
    boolean existsActiveInvoice(@Param("companyId") Long companyId, @Param("debtType") DebtType debtType,
                                @Param("partnerId") Long partnerId, @Param("invoiceNo") String invoiceNo);

    /** Khoản công nợ còn nợ của đối tượng trong chi nhánh, khóa ghi theo id tăng dần (phân bổ tự động). */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select i from DebtItem i
            where i.companyId = :companyId and i.branchId = :branchId and i.debtType = :debtType
              and i.partnerId = :partnerId
              and i.status in (com.company.erp.finance.domain.DebtItemStatus.OPEN,
                               com.company.erp.finance.domain.DebtItemStatus.PARTIAL)
            order by i.id
            """)
    List<DebtItem> findOpenForUpdate(@Param("companyId") Long companyId, @Param("branchId") Long branchId,
                                     @Param("debtType") DebtType debtType, @Param("partnerId") Long partnerId);

    /** Khóa ghi các khoản công nợ theo id tăng dần (thứ tự khóa cố định, docs/02 mục 6). */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from DebtItem i where i.id in :ids order by i.id")
    List<DebtItem> findAllForUpdate(@Param("ids") Collection<Long> ids);

    /** Số còn nợ của đối tượng (toàn công ty, mọi chi nhánh) và phần quá hạn tại ngày {@code asOf}. */
    @Query("""
            select coalesce(sum(i.originalAmount - i.settledAmount), 0),
                   coalesce(sum(case when i.dueDate < :asOf then i.originalAmount - i.settledAmount else 0 end), 0)
            from DebtItem i
            where i.companyId = :companyId and i.debtType = :debtType and i.partnerId = :partnerId
              and i.status <> com.company.erp.finance.domain.DebtItemStatus.REVERSED
            """)
    List<Object[]> exposure(@Param("companyId") Long companyId, @Param("debtType") DebtType debtType,
                            @Param("partnerId") Long partnerId, @Param("asOf") LocalDate asOf);
}
