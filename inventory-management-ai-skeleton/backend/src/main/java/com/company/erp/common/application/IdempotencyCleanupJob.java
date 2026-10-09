package com.company.erp.common.application;

import com.company.erp.common.infrastructure.persistence.IdempotencyRecordRepository;
import java.time.Clock;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Xóa key chống trùng đã hết hạn. Chạy lại nhiều lần không gây tác dụng phụ (BR-GEN-54). */
@Component
class IdempotencyCleanupJob {

    private static final Logger log = LoggerFactory.getLogger(IdempotencyCleanupJob.class);

    private final IdempotencyRecordRepository repository;
    private final Clock clock;

    IdempotencyCleanupJob(IdempotencyRecordRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @Scheduled(cron = "${erp.idempotency.cleanup-cron}")
    @Transactional
    public void deleteExpired() {
        int deleted = repository.deleteExpired(Instant.now(clock));
        if (deleted > 0) {
            log.info("Đã xóa {} Idempotency-Key hết hạn", deleted);
        }
    }
}
