package com.company.erp.purchasing.infrastructure.persistence;

import com.company.erp.purchasing.domain.PurchaseDocumentStatus;
import com.company.erp.purchasing.domain.PurchaseRequisition;
import jakarta.persistence.LockModeType;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PurchaseRequisitionRepository extends JpaRepository<PurchaseRequisition, Long>,
        JpaSpecificationExecutor<PurchaseRequisition> {

    /** Dòng đề nghị còn số chưa đặt (đơn vị cơ sở). */
    interface OpenLineRow {
        Long getRequisitionId();

        String getDocumentNo();

        LocalDate getDocumentDate();

        Long getLineId();

        Integer getLineNo();

        Long getProductId();

        BigDecimal getRemaining();

        Long getSuggestedSupplierId();

        LocalDate getRequiredDate();
    }

    @Query("""
            select d.id as requisitionId, d.documentNo as documentNo, d.documentDate as documentDate, l.id as lineId,
                   l.lineNo as lineNo, l.productId as productId,
                   (l.baseQuantity - l.orderedQuantity - l.closedQuantity) as remaining,
                   l.suggestedSupplierId as suggestedSupplierId, l.requiredDate as requiredDate
            from PurchaseRequisition d join d.lines l
            where d.companyId = :companyId and d.branchId = :branchId and d.status in :statuses
              and (:productId is null or l.productId = :productId)
              and l.baseQuantity - l.orderedQuantity - l.closedQuantity > 0
            order by d.documentDate, d.id, l.lineNo
            """)
    List<OpenLineRow> findOpenLines(@Param("companyId") Long companyId, @Param("branchId") Long branchId,
                                    @Param("statuses") Collection<PurchaseDocumentStatus> statuses,
                                    @Param("productId") Long productId);

    /** Khóa ghi chứng từ trước khi đổi trạng thái (BR-GEN-51). */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select d from PurchaseRequisition d where d.id = :id")
    Optional<PurchaseRequisition> findByIdForUpdate(@Param("id") Long id);

    /** Id đề nghị chứa các dòng, tăng dần (khóa từng đề nghị theo thứ tự này). */
    @Query("select distinct d.id from PurchaseRequisition d join d.lines l where l.id in :lineIds order by d.id")
    List<Long> findIdsByLineIds(@Param("lineIds") Collection<Long> lineIds);

    @Query("""
            select distinct d from PurchaseRequisition d join d.lines l
            where l.id in :lineIds
            """)
    List<PurchaseRequisition> findByLineIds(@Param("lineIds") Collection<Long> lineIds);
}
