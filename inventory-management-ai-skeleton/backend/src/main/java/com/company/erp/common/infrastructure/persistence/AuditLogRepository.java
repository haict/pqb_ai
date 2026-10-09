package com.company.erp.common.infrastructure.persistence;

import com.company.erp.common.domain.AuditLog;
import java.util.List;
import org.springframework.data.repository.Repository;

/**
 * Chỉ cho phép thêm và đọc; không có phương thức sửa/xóa (nhật ký không sửa/xóa qua ứng dụng, BR-SYS-20).
 */
public interface AuditLogRepository extends Repository<AuditLog, Long> {

    AuditLog save(AuditLog auditLog);

    List<AuditLog> findByEntityTypeAndEntityIdOrderByOccurredAtAsc(String entityType, Long entityId);
}
