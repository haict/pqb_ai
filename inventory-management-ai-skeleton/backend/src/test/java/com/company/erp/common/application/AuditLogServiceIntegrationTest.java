package com.company.erp.common.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.company.erp.common.domain.AuditLog;
import com.company.erp.common.infrastructure.persistence.AuditLogRepository;
import com.company.erp.support.MySqlIntegrationTest;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.support.TransactionTemplate;

@MySqlIntegrationTest
class AuditLogServiceIntegrationTest {

    private final AuditLogService service;
    private final AuditLogRepository repository;
    private final TransactionTemplate transactionTemplate;

    @Autowired
    AuditLogServiceIntegrationTest(AuditLogService service, AuditLogRepository repository,
                                   TransactionTemplate transactionTemplate) {
        this.service = service;
        this.repository = repository;
        this.transactionTemplate = transactionTemplate;
    }

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    @DisplayName("BR-GEN-07: lưu người, hành động, lý do, dữ liệu trước/sau và request ID")
    void recordsAuditEntry() {
        MDC.put(RequestContext.REQUEST_ID, "req-audit-0001");
        MDC.put(RequestContext.CLIENT_IP, "10.0.0.5");

        transactionTemplate.executeWithoutResult(status -> service.record(new AuditEntry(
                1L, "REVERSE", "TEST_DOCUMENT", 1001L, "PXK-HN-2026-000001", "Xuất nhầm lô",
                Map.of("status", "POSTED"), Map.of("status", "REVERSED"), 1L, 1L)));

        List<AuditLog> logs = repository.findByEntityTypeAndEntityIdOrderByOccurredAtAsc("TEST_DOCUMENT", 1001L);
        assertThat(logs).hasSize(1);
        AuditLog log = logs.get(0);
        assertThat(log.getAction()).isEqualTo("REVERSE");
        assertThat(log.getReason()).isEqualTo("Xuất nhầm lô");
        assertThat(log.getBeforeValue()).contains("POSTED");
        assertThat(log.getAfterValue()).contains("REVERSED");
        assertThat(log.getRequestId()).isEqualTo("req-audit-0001");
    }

    @Test
    @DisplayName("Nghiệp vụ rollback thì nhật ký cũng rollback")
    void auditRollsBackWithBusiness() {
        transactionTemplate.executeWithoutResult(status -> {
            service.record(new AuditEntry(1L, "POST", "TEST_DOCUMENT", 2002L, null, null, null, null, 1L, 1L));
            status.setRollbackOnly();
        });

        assertThat(repository.findByEntityTypeAndEntityIdOrderByOccurredAtAsc("TEST_DOCUMENT", 2002L)).isEmpty();
    }

    @Test
    @DisplayName("Bắt buộc gọi trong transaction nghiệp vụ")
    void requiresTransaction() {
        assertThatThrownBy(() -> service.record(
                new AuditEntry(1L, "POST", "TEST_DOCUMENT", 3003L, null, null, null, null, 1L, 1L)))
                .isInstanceOf(IllegalTransactionStateException.class);
    }
}
