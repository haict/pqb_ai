package com.company.erp.sales.infrastructure.persistence;

import com.company.erp.sales.domain.SalesOrder;
import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SalesOrderRepository extends JpaRepository<SalesOrder, Long>, JpaSpecificationExecutor<SalesOrder> {

    /** Khóa ghi chứng từ trước khi đổi trạng thái (BR-GEN-51). */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select d from SalesOrder d where d.id = :id")
    Optional<SalesOrder> findByIdForUpdate(@Param("id") Long id);

    /** Id chứng từ chứa các dòng, tăng dần (khóa từng chứng từ theo thứ tự này). */
    @Query("select distinct d.id from SalesOrder d join d.lines l where l.id in :lineIds order by d.id")
    List<Long> findIdsByLineIds(@Param("lineIds") Collection<Long> lineIds);

    @Query("select distinct d from SalesOrder d join d.lines l where l.id in :lineIds")
    List<SalesOrder> findByLineIds(@Param("lineIds") Collection<Long> lineIds);

    /** Đơn đã duyệt (còn mở hoặc đã hoàn thành) của khách trong chi nhánh: dòng dịch vụ còn xuất hóa đơn được. */
    @Query("""
            select d from SalesOrder d
            where d.companyId = :companyId and d.branchId = :branchId and d.customerId = :customerId
              and d.status in (com.company.erp.sales.domain.SalesDocumentStatus.APPROVED,
                               com.company.erp.sales.domain.SalesDocumentStatus.IN_PROGRESS,
                               com.company.erp.sales.domain.SalesDocumentStatus.COMPLETED)
            order by d.documentDate, d.id
            """)
    List<SalesOrder> findApprovedByCustomer(@Param("companyId") Long companyId, @Param("branchId") Long branchId,
                                            @Param("customerId") Long customerId);
}
