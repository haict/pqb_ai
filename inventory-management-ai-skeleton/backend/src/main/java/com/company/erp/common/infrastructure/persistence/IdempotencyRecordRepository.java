package com.company.erp.common.infrastructure.persistence;

import com.company.erp.common.domain.IdempotencyRecord;
import java.time.Instant;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface IdempotencyRecordRepository extends JpaRepository<IdempotencyRecord, Long> {

    Optional<IdempotencyRecord> findByUserIdAndIdempotencyKey(Long userId, String idempotencyKey);

    @Modifying
    @Query("delete from IdempotencyRecord r where r.expiresAt <= :now")
    int deleteExpired(@Param("now") Instant now);
}
