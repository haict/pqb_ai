package com.company.erp.system.application;

import com.company.erp.common.application.AuditEntry;
import com.company.erp.common.application.AuditLogService;
import com.company.erp.common.application.IdempotencyService;
import com.company.erp.common.application.IdempotentCommand;
import com.company.erp.common.domain.BusinessException;
import com.company.erp.common.domain.ErrorCode;
import com.company.erp.common.domain.PeriodModule;
import com.company.erp.system.application.dto.FiscalPeriodResponse;
import com.company.erp.system.application.dto.GeneratePeriodsRequest;
import com.company.erp.system.application.dto.GeneratePeriodsResponse;
import com.company.erp.system.application.dto.PeriodLockStatus;
import com.company.erp.system.domain.Branch;
import com.company.erp.system.domain.FiscalPeriod;
import com.company.erp.system.domain.PeriodLock;
import com.company.erp.system.domain.User;
import com.company.erp.system.infrastructure.persistence.BranchRepository;
import com.company.erp.system.infrastructure.persistence.CompanyRepository;
import com.company.erp.system.infrastructure.persistence.FiscalPeriodRepository;
import com.company.erp.system.infrastructure.persistence.PeriodLockRepository;
import com.company.erp.system.infrastructure.persistence.UserRepository;
import java.time.YearMonth;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Tạo kỳ tháng và xem trạng thái khóa kỳ theo chi nhánh (BR-SYS-30). */
@Service
public class FiscalPeriodService {

    private static final String ENDPOINT_GENERATE = "POST /api/v1/fiscal-periods/generate";
    private static final int MONTHS = 12;

    private final FiscalPeriodRepository periodRepository;
    private final PeriodLockRepository lockRepository;
    private final CompanyRepository companyRepository;
    private final BranchRepository branchRepository;
    private final UserRepository userRepository;
    private final IdempotencyService idempotency;
    private final AuditLogService auditLog;

    public FiscalPeriodService(FiscalPeriodRepository periodRepository, PeriodLockRepository lockRepository,
                               CompanyRepository companyRepository, BranchRepository branchRepository,
                               UserRepository userRepository, IdempotencyService idempotency,
                               AuditLogService auditLog) {
        this.periodRepository = periodRepository;
        this.lockRepository = lockRepository;
        this.companyRepository = companyRepository;
        this.branchRepository = branchRepository;
        this.userRepository = userRepository;
        this.idempotency = idempotency;
        this.auditLog = auditLog;
    }

    /** Kỳ của một năm (tối đa 12 dòng) kèm trạng thái khóa của chi nhánh {@code branchId}. */
    @Transactional(readOnly = true)
    public List<FiscalPeriodResponse> list(CurrentUser actor, Long companyId, int year, Long branchId) {
        Branch branch = findVisibleBranch(actor, branchId);
        if (!branch.getCompanyId().equals(companyId)) {
            throw BusinessException.notFound();
        }
        List<FiscalPeriod> periods = periodRepository.findByCompanyIdAndYearOrderByMonth(companyId, year);
        List<PeriodLock> locks = lockRepository.findByFiscalPeriodIdInAndBranchId(
                periods.stream().map(FiscalPeriod::getId).toList(), branchId);
        Map<Long, String> usernames = usernames(locks);
        return periods.stream().map(period -> toResponse(period, locks, usernames)).toList();
    }

    @Transactional
    public GeneratePeriodsResponse generate(CurrentUser actor, String idempotencyKey, GeneratePeriodsRequest request) {
        IdempotentCommand command = new IdempotentCommand(actor.id(), idempotencyKey, ENDPOINT_GENERATE, request);
        return idempotency.execute(command, GeneratePeriodsResponse.class, () -> {
            Long companyId = request.companyId();
            requireFullCompanyAccess(actor, companyId);
            int created = 0;
            for (int month = 1; month <= MONTHS; month++) {
                if (periodRepository.findByCompanyIdAndYearAndMonth(companyId, request.year(), month).isEmpty()) {
                    periodRepository.save(new FiscalPeriod(companyId, YearMonth.of(request.year(), month)));
                    created++;
                }
            }
            periodRepository.flush();
            GeneratePeriodsResponse response = new GeneratePeriodsResponse(companyId, request.year(), created);
            auditLog.record(new AuditEntry(actor.id(), AuditActions.CREATE, AuditActions.FISCAL_PERIOD, null, null,
                    null, null, response, companyId, null));
            return response;
        });
    }

    private FiscalPeriodResponse toResponse(FiscalPeriod period, List<PeriodLock> locks, Map<Long, String> usernames) {
        Map<PeriodModule, PeriodLock> byModule = locks.stream()
                .filter(lock -> lock.getFiscalPeriodId().equals(period.getId()))
                .collect(Collectors.toMap(PeriodLock::getModule, Function.identity()));
        List<PeriodLockStatus> statuses = Arrays.stream(PeriodModule.values())
                .map(module -> {
                    PeriodLock lock = byModule.get(module);
                    return lock == null
                            ? new PeriodLockStatus(module, false, null, null, null)
                            : new PeriodLockStatus(module, lock.isLocked(), lock.getChangedAt(),
                                    usernames.get(lock.getChangedBy()), lock.getReason());
                })
                .toList();
        return new FiscalPeriodResponse(period.getId(), period.getCompanyId(), period.getYear(), period.getMonth(),
                period.getStartDate(), period.getEndDate(), statuses);
    }

    private Map<Long, String> usernames(List<PeriodLock> locks) {
        List<Long> userIds = locks.stream().map(PeriodLock::getChangedBy).filter(Objects::nonNull).distinct().toList();
        return userRepository.findAllById(userIds).stream()
                .collect(Collectors.toMap(User::getId, User::getUsername));
    }

    private Branch findVisibleBranch(CurrentUser actor, Long branchId) {
        if (!actor.dataScope().canAccessBranch(branchId)) {
            throw BusinessException.notFound();
        }
        return branchRepository.findById(branchId).orElseThrow(BusinessException::notFound);
    }

    private void requireFullCompanyAccess(CurrentUser actor, Long companyId) {
        if (!actor.dataScope().canAccessCompany(companyId) || !companyRepository.existsById(companyId)) {
            throw BusinessException.notFound();
        }
        if (!actor.dataScope().hasFullCompanyAccess(companyId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "Cần phạm vi dữ liệu toàn công ty để tạo kỳ.");
        }
    }
}
