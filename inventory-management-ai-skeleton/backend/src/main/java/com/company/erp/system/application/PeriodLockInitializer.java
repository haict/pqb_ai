package com.company.erp.system.application;

import com.company.erp.common.domain.PeriodModule;
import com.company.erp.system.infrastructure.persistence.FiscalPeriodRepository;
import com.company.erp.system.infrastructure.persistence.PeriodLockRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Tạo dòng trạng thái khóa trong transaction riêng và commit ngay. Transaction khóa/mở kỳ sau đó chỉ khóa dòng
 * đã tồn tại; khóa dòng chưa tồn tại (gap lock) có thể gây deadlock khi hai transaction cùng chèn vào một khoảng.
 * Khóa ghi các kỳ trước khi chèn để hai lần tạo đồng thời chạy tuần tự (INSERT trùng khóa đồng thời gây deadlock).
 */
@Component
class PeriodLockInitializer {

    private final FiscalPeriodRepository periodRepository;
    private final PeriodLockRepository repository;
    private final Clock clock;

    PeriodLockInitializer(FiscalPeriodRepository periodRepository, PeriodLockRepository repository, Clock clock) {
        this.periodRepository = periodRepository;
        this.repository = repository;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void ensureExists(List<Long> fiscalPeriodIds, List<Long> branchIds) {
        periodRepository.lockAllById(fiscalPeriodIds);
        Instant now = Instant.now(clock);
        for (Long periodId : fiscalPeriodIds) {
            for (Long branchId : branchIds) {
                for (PeriodModule module : PeriodModule.values()) {
                    repository.insertIfAbsent(periodId, branchId, module.name(), now);
                }
            }
        }
    }
}
