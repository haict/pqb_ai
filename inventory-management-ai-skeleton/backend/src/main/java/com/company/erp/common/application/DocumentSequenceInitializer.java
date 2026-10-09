package com.company.erp.common.application;

import com.company.erp.common.infrastructure.persistence.DocumentSequenceRepository;
import java.time.Clock;
import java.time.Instant;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Tạo dòng bộ đếm trong transaction riêng và commit ngay, để transaction nghiệp vụ
 * chỉ cần khóa một dòng đã tồn tại. Tránh hai transaction cùng chèn dòng đầu tiên rồi xung đột.
 */
@Component
class DocumentSequenceInitializer {

    private final DocumentSequenceRepository repository;
    private final Clock clock;

    DocumentSequenceInitializer(DocumentSequenceRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void ensureExists(Long companyId, Long branchId, String documentType, String periodKey) {
        repository.insertIfAbsent(companyId, branchId, documentType, periodKey, Instant.now(clock));
    }
}
