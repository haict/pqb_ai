package com.company.erp.sales.infrastructure.persistence;

import com.company.erp.sales.domain.SalesInvoice;
import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SalesInvoiceRepository extends JpaRepository<SalesInvoice, Long>, JpaSpecificationExecutor<SalesInvoice> {

    /** Khóa ghi chứng từ trước khi đổi trạng thái (BR-GEN-51). */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select d from SalesInvoice d where d.id = :id")
    Optional<SalesInvoice> findByIdForUpdate(@Param("id") Long id);

    /** Id chứng từ chứa các dòng, tăng dần (khóa từng chứng từ theo thứ tự này). */
    @Query("select distinct d.id from SalesInvoice d join d.lines l where l.id in :lineIds order by d.id")
    List<Long> findIdsByLineIds(@Param("lineIds") Collection<Long> lineIds);

    @Query("select distinct d from SalesInvoice d join d.lines l where l.id in :lineIds")
    List<SalesInvoice> findByLineIds(@Param("lineIds") Collection<Long> lineIds);

    /** Hóa đơn khác (đang hiệu lực) trùng ký hiệu + số hóa đơn điện tử. */
    @Query("""
            select d.documentNo from SalesInvoice d
            where d.companyId = :companyId and d.einvoiceKey = :key and d.id <> :id
            """)
    Optional<String> findEinvoiceDuplicate(@Param("companyId") Long companyId, @Param("key") String key,
                                           @Param("id") Long id);

    /** Hóa đơn đã ghi sổ có dòng thuộc đơn bán: mốc lịch thu tiền, tiền đã thu theo đơn (BR-SAL-50). */
    @Query("""
            select distinct d from SalesInvoice d join d.lines l
            where d.status = com.company.erp.sales.domain.SalesDocumentStatus.POSTED
              and l.orderLineId in (select x.id from SalesOrder o join o.lines x where o.id = :orderId)
            """)
    List<SalesInvoice> findPostedByOrder(@Param("orderId") Long orderId);
}
