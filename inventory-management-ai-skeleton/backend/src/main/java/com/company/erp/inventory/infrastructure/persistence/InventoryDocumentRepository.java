package com.company.erp.inventory.infrastructure.persistence;

import com.company.erp.inventory.domain.InventoryDocument;
import com.company.erp.inventory.domain.InventoryDocumentStatus;
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

public interface InventoryDocumentRepository extends JpaRepository<InventoryDocument, Long>, JpaSpecificationExecutor<InventoryDocument> {

    /** Khóa ghi chứng từ theo khóa chính trước khi đổi trạng thái (BR-GEN-51, BR-GEN-52). */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select d from InventoryDocument d where d.id = :id")
    Optional<InventoryDocument> findByIdForUpdate(@Param("id") Long id);

    /** Chứng từ đã duyệt / đang vận chuyển chưa ghi sổ xong trong khoảng ngày hạch toán (điều kiện khóa kỳ kho). */
    @Query("""
            select d.documentNo from InventoryDocument d
            where d.companyId = :companyId and (:branchId is null or d.branchId = :branchId)
              and d.status in :statuses and d.postingDate between :startDate and :endDate
            order by d.documentNo
            """)
    List<String> findUnfinishedNumbers(@Param("companyId") Long companyId, @Param("branchId") Long branchId,
                                       @Param("statuses") Collection<InventoryDocumentStatus> statuses,
                                       @Param("startDate") LocalDate startDate, @Param("endDate") LocalDate endDate);
}
