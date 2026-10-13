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

    /** Đơn bán khác (chưa hủy / đảo) đã dùng cùng số hóa đơn điện tử (BR-SAL-31). */
    @Query("""
            select d.documentNo from SalesOrder d
            where d.companyId = :companyId and d.einvoiceKey = :key and (:id is null or d.id <> :id)
            """)
    Optional<String> findEinvoiceDuplicate(@Param("companyId") Long companyId, @Param("key") String key,
                                           @Param("id") Long id);
}
