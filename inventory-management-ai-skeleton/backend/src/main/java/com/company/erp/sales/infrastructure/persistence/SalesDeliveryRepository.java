package com.company.erp.sales.infrastructure.persistence;

import com.company.erp.sales.domain.SalesDelivery;
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

public interface SalesDeliveryRepository extends JpaRepository<SalesDelivery, Long>, JpaSpecificationExecutor<SalesDelivery> {

    /** Khóa ghi chứng từ trước khi đổi trạng thái (BR-GEN-51). */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select d from SalesDelivery d where d.id = :id")
    Optional<SalesDelivery> findByIdForUpdate(@Param("id") Long id);

    /** Id chứng từ chứa các dòng, tăng dần (khóa từng chứng từ theo thứ tự này). */
    @Query("select distinct d.id from SalesDelivery d join d.lines l where l.id in :lineIds order by d.id")
    List<Long> findIdsByLineIds(@Param("lineIds") Collection<Long> lineIds);

    @Query("select distinct d from SalesDelivery d join d.lines l where l.id in :lineIds")
    List<SalesDelivery> findByLineIds(@Param("lineIds") Collection<Long> lineIds);

    /** Phiếu giao của đơn bán (truy vết đơn → phiếu giao). */
    List<SalesDelivery> findByOrderIdOrderByIdAsc(Long orderId);

    /** Ngày khách nhận đầu tiên của đơn (mốc lịch thu tiền "sau giao hàng", BR-SAL-50). */
    @Query("""
            select min(d.receivedDate) from SalesDelivery d
            where d.orderId = :orderId and d.deliveryStatus = com.company.erp.sales.domain.DeliveryStatus.DELIVERED
            """)
    Optional<LocalDate> firstReceivedDate(@Param("orderId") Long orderId);

    /** Phiếu giao đã xuất kho của khách trong chi nhánh (chọn dòng lập hóa đơn / trả hàng). */
    @Query("""
            select d from SalesDelivery d
            where d.companyId = :companyId and d.branchId = :branchId and d.customerId = :customerId
              and d.status = com.company.erp.sales.domain.SalesDocumentStatus.POSTED
            order by d.postingDate, d.id
            """)
    List<SalesDelivery> findPostedByCustomer(@Param("companyId") Long companyId, @Param("branchId") Long branchId,
                                             @Param("customerId") Long customerId);
}
