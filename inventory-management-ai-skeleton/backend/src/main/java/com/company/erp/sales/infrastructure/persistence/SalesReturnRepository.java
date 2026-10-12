package com.company.erp.sales.infrastructure.persistence;

import com.company.erp.sales.domain.SalesReturn;
import jakarta.persistence.LockModeType;
import java.math.BigDecimal;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SalesReturnRepository extends JpaRepository<SalesReturn, Long>, JpaSpecificationExecutor<SalesReturn> {

    /** Khóa ghi chứng từ trước khi đổi trạng thái (BR-GEN-51). */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select d from SalesReturn d where d.id = :id")
    Optional<SalesReturn> findByIdForUpdate(@Param("id") Long id);

    /** Số đã trả (chứng từ đã ghi sổ) theo dòng phiếu giao và lô: không trả vượt số đã xuất của lô (BR-SAL-32). */
    @Query("""
            select coalesce(sum(l.baseQuantity), 0) from SalesReturn d join d.lines l
            where d.status = com.company.erp.sales.domain.SalesDocumentStatus.POSTED
              and l.deliveryLineId = :deliveryLineId and ((:lotNo is null and l.lotNo is null) or l.lotNo = :lotNo)
            """)
    BigDecimal returnedForLot(@Param("deliveryLineId") Long deliveryLineId, @Param("lotNo") String lotNo);
}
