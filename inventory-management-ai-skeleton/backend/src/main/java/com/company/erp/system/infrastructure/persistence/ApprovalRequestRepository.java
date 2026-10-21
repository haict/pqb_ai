package com.company.erp.system.infrastructure.persistence;

import com.company.erp.system.domain.ApprovalRequest;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ApprovalRequestRepository extends JpaRepository<ApprovalRequest, Long> {

    /** ID yêu cầu đang chờ của chứng từ (đọc không khóa). */
    @Query("""
            select r.id from ApprovalRequest r
            where r.documentType = :documentType and r.documentId = :documentId
              and r.status = com.company.erp.system.domain.ApprovalRequestStatus.PENDING
            """)
    Optional<Long> findPendingId(@Param("documentType") String documentType, @Param("documentId") Long documentId);

    /**
     * Khóa ghi theo khóa chính. Không khóa qua unique index có cột sinh {@code pending_flag}: cập nhật trạng thái làm đổi
     * index đó, hai giao dịch khóa qua index phụ rồi cập nhật sẽ deadlock.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from ApprovalRequest r where r.id = :id")
    Optional<ApprovalRequest> findByIdForUpdate(@Param("id") Long id);

    List<ApprovalRequest> findByDocumentTypeAndDocumentIdOrderByRequestedAtDescIdDesc(String documentType,
                                                                                    Long documentId);

}
