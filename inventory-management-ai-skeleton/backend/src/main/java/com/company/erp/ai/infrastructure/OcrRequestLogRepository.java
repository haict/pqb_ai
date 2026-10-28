package com.company.erp.ai.infrastructure;

import com.company.erp.ai.domain.OcrRequestLog;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OcrRequestLogRepository extends JpaRepository<OcrRequestLog, Long> {
}
