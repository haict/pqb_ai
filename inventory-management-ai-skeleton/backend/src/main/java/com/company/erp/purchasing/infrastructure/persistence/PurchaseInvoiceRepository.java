package com.company.erp.purchasing.infrastructure.persistence;

import com.company.erp.purchasing.domain.PurchaseDocumentStatus;
import com.company.erp.purchasing.domain.PurchaseInvoice;
import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PurchaseInvoiceRepository extends JpaRepository<PurchaseInvoice, Long>,
        JpaSpecificationExecutor<PurchaseInvoice> {

    /** Khóa ghi chứng từ trước khi đổi trạng thái (BR-GEN-51). */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select d from PurchaseInvoice d where d.id = :id")
    Optional<PurchaseInvoice> findByIdForUpdate(@Param("id") Long id);

    /** Hóa đơn khác (đang hiệu lực) trùng MST người bán, ký hiệu, số (BR-PUR-09). */
    @Query("""
            select d.documentNo from PurchaseInvoice d
            where d.companyId = :companyId and d.duplicateKey = :key and (:id is null or d.id <> :id)
            """)
    Optional<String> findDuplicate(@Param("companyId") Long companyId, @Param("key") String key,
                                   @Param("id") Long id);

    /** Id hóa đơn chứa các dòng, tăng dần (khóa từng hóa đơn theo thứ tự này). */
    @Query("select distinct d.id from PurchaseInvoice d join d.lines l where l.id in :lineIds order by d.id")
    List<Long> findIdsByLineIds(@Param("lineIds") Collection<Long> lineIds);

    @Query("select distinct d from PurchaseInvoice d join d.lines l where l.id in :lineIds")
    List<PurchaseInvoice> findByLineIds(@Param("lineIds") Collection<Long> lineIds);

    /** Hóa đơn đã ghi sổ của chi nhánh có dòng dịch vụ còn chưa phân bổ (chi phí mua hàng, BR-PUR-12). */
    @Query("""
            select distinct d from PurchaseInvoice d join d.lines l
            where d.companyId = :companyId and d.branchId = :branchId and d.status = :posted
              and l.receiptLineId is null and l.amount - l.adjustedAmount - l.allocatedAmount > 0
            order by d.id desc
            """)
    List<PurchaseInvoice> findWithUnallocatedServices(@Param("companyId") Long companyId,
                                                      @Param("branchId") Long branchId,
                                                      @Param("posted") PurchaseDocumentStatus posted);
}
