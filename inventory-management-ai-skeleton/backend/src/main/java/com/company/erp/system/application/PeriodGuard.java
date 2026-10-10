package com.company.erp.system.application;

import com.company.erp.common.domain.BusinessException;
import com.company.erp.common.domain.ErrorCode;
import com.company.erp.common.domain.PeriodModule;
import com.company.erp.system.domain.FiscalPeriod;
import com.company.erp.system.domain.PeriodLock;
import com.company.erp.system.infrastructure.persistence.FiscalPeriodRepository;
import com.company.erp.system.infrastructure.persistence.PeriodLockRepository;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Chặn ghi sổ, đảo, sửa chứng từ có ngày hạch toán thuộc kỳ đã khóa hoặc chưa tạo (BR-GEN-10, BR-SYS-30, AT-11).
 * Module ghi sổ bắt buộc gọi trong transaction ghi sổ, trước khi ghi sổ phụ.
 *
 * <p>Kỳ được đọc với khóa chia sẻ (FOR SHARE) giữ đến hết transaction: thao tác khóa kỳ (FOR UPDATE trên
 * cùng dòng) phải chờ giao dịch ghi sổ commit, và ngược lại giao dịch ghi sổ thấy trạng thái khóa mới nhất.
 */
@Service
public class PeriodGuard {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final FiscalPeriodRepository periodRepository;
    private final PeriodLockRepository lockRepository;

    public PeriodGuard(FiscalPeriodRepository periodRepository, PeriodLockRepository lockRepository) {
        this.periodRepository = periodRepository;
        this.lockRepository = lockRepository;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void assertOpen(Long companyId, Long branchId, PeriodModule module, LocalDate postingDate) {
        FiscalPeriod period = periodRepository.findCoveringForShare(companyId, postingDate)
                .orElseThrow(() -> new BusinessException(ErrorCode.PERIOD_NOT_DEFINED,
                        "Chưa tạo kỳ cho ngày hạch toán " + DATE.format(postingDate) + "."));
        boolean locked = lockRepository.findForShare(period.getId(), branchId, module)
                .map(PeriodLock::isLocked)
                .orElse(false);
        if (locked) {
            throw new BusinessException(ErrorCode.PERIOD_LOCKED, "Kỳ " + PeriodModuleLabels.of(module) + " "
                    + period.label() + " đã khóa, không ghi sổ, đảo hoặc sửa chứng từ có ngày hạch toán "
                    + DATE.format(postingDate) + ".");
        }
    }
}
