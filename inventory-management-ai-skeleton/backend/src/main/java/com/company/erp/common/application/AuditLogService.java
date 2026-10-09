package com.company.erp.common.application;

import com.company.erp.common.domain.AuditLog;
import com.company.erp.common.infrastructure.persistence.AuditLogRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Ghi nhật ký thao tác (BR-GEN-07, BR-SYS-20) trong cùng transaction với nghiệp vụ,
 * để không có thao tác nào được commit mà thiếu nhật ký.
 *
 * <p>Không truyền mật khẩu, token hay dữ liệu nhạy cảm vào {@code before}/{@code after}.
 */
@Service
public class AuditLogService {

    private final AuditLogRepository repository;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public AuditLogService(AuditLogRepository repository, ObjectMapper objectMapper, Clock clock) {
        this.repository = repository;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void record(AuditEntry entry) {
        AuditLog log = new AuditLog(
                Instant.now(clock),
                entry.actorUserId(),
                entry.action(),
                entry.entityType(),
                entry.entityId(),
                entry.documentNo(),
                entry.reason(),
                toJson(entry.before()),
                toJson(entry.after()),
                RequestContext.requestId(),
                RequestContext.clientIp(),
                entry.companyId(),
                entry.branchId());
        repository.save(log);
    }

    private String toJson(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Không chuyển được dữ liệu nhật ký sang JSON", ex);
        }
    }
}
