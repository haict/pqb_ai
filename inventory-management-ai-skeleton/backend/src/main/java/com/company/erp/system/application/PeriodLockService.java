package com.company.erp.system.application;

import com.company.erp.common.application.AuditEntry;
import com.company.erp.common.application.AuditLogService;
import com.company.erp.common.application.IdempotencyService;
import com.company.erp.common.application.IdempotentCommand;
import com.company.erp.common.domain.BusinessException;
import com.company.erp.common.domain.ErrorCode;
import com.company.erp.common.domain.PeriodModule;
import com.company.erp.system.application.dto.LockPeriodRequest;
import com.company.erp.system.application.dto.PeriodLockResult;
import com.company.erp.system.application.dto.UnlockPeriodRequest;
import com.company.erp.system.domain.Branch;
import com.company.erp.system.domain.FiscalPeriod;
import com.company.erp.system.domain.PeriodLock;
import com.company.erp.system.infrastructure.persistence.BranchRepository;
import com.company.erp.system.infrastructure.persistence.FiscalPeriodRepository;
import com.company.erp.system.infrastructure.persistence.PeriodLockRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Khóa và mở lại kỳ theo chi nhánh × phân hệ (BR-SYS-30..32, AT-11).
 *
 * <ul>
 *   <li>Khóa theo thứ tự thời gian: kỳ trước (nếu có) phải đã khóa; mở lại theo thứ tự ngược: kỳ sau phải đang mở.</li>
 *   <li>Kỳ kế toán chỉ khóa sau kỳ kho cùng tháng; kỳ kho chỉ mở lại khi kỳ kế toán cùng tháng đang mở (BR-SYS-31).</li>
 *   <li>Điều kiện của module nghiệp vụ (ví dụ đã tính giá vốn) qua {@link PeriodCloseCheck}.</li>
 *   <li>Mở lại cần quyền {@code period:unlock} (Kế toán trưởng, Giám đốc) và lý do (BR-SYS-32).</li>
 * </ul>
 * Mỗi thao tác khóa ghi (FOR UPDATE) kỳ đích và kỳ liền kề theo thứ tự id, nên hai thao tác trên hai kỳ liền kề
 * không thể cùng vượt qua kiểm tra thứ tự. Nhiều chi nhánh trong một yêu cầu: tất cả cùng thành công hoặc không.
 */
@Service
public class PeriodLockService {

    private final FiscalPeriodRepository periodRepository;
    private final PeriodLockRepository lockRepository;
    private final BranchRepository branchRepository;
    private final ObjectProvider<PeriodCloseCheck> closeChecks;
    private final PeriodLockInitializer initializer;
    private final IdempotencyService idempotency;
    private final AuditLogService auditLog;
    private final Clock clock;

    public PeriodLockService(FiscalPeriodRepository periodRepository, PeriodLockRepository lockRepository,
                             BranchRepository branchRepository, ObjectProvider<PeriodCloseCheck> closeChecks,
                             PeriodLockInitializer initializer, IdempotencyService idempotency,
                             AuditLogService auditLog, Clock clock) {
        this.periodRepository = periodRepository;
        this.lockRepository = lockRepository;
        this.branchRepository = branchRepository;
        this.closeChecks = closeChecks;
        this.initializer = initializer;
        this.idempotency = idempotency;
        this.auditLog = auditLog;
        this.clock = clock;
    }

    @Transactional
    public PeriodLockResult lock(CurrentUser actor, Long periodId, String idempotencyKey, LockPeriodRequest request) {
        IdempotentCommand command = new IdempotentCommand(actor.id(), idempotencyKey,
                "POST /api/v1/fiscal-periods/" + periodId + "/lock", request);
        return idempotency.execute(command, PeriodLockResult.class, () -> {
            FiscalPeriod period = findVisiblePeriod(actor, periodId);
            List<Branch> branches = checkedBranches(actor, period, request.branchIds());
            Optional<FiscalPeriod> previous = adjacent(period, -1);
            prepare(period, previous, branches);
            Instant now = Instant.now(clock);
            for (Branch branch : branches) {
                lockOne(actor, period, previous, branch, request.module(), request.reason(), now);
            }
            return new PeriodLockResult(period.getId(), request.module(), true,
                    branches.stream().map(Branch::getId).toList());
        });
    }

    @Transactional
    public PeriodLockResult unlock(CurrentUser actor, Long periodId, String idempotencyKey,
                                   UnlockPeriodRequest request) {
        IdempotentCommand command = new IdempotentCommand(actor.id(), idempotencyKey,
                "POST /api/v1/fiscal-periods/" + periodId + "/unlock", request);
        return idempotency.execute(command, PeriodLockResult.class, () -> {
            FiscalPeriod period = findVisiblePeriod(actor, periodId);
            List<Branch> branches = checkedBranches(actor, period, request.branchIds());
            Optional<FiscalPeriod> next = adjacent(period, 1);
            prepare(period, next, branches);
            Instant now = Instant.now(clock);
            for (Branch branch : branches) {
                unlockOne(actor, period, next, branch, request.module(), request.reason().trim(), now);
            }
            return new PeriodLockResult(period.getId(), request.module(), false,
                    branches.stream().map(Branch::getId).toList());
        });
    }

    private void lockOne(CurrentUser actor, FiscalPeriod period, Optional<FiscalPeriod> previous, Branch branch,
                         PeriodModule module, String reason, Instant now) {
        String scope = describe(period, branch, module);
        PeriodLock lock = lockRepository.findForUpdate(period.getId(), branch.getId(), module)
                .orElseThrow(() -> new IllegalStateException("Thiếu dòng trạng thái khóa kỳ"));
        if (lock.isLocked()) {
            throw new BusinessException(ErrorCode.INVALID_PERIOD_STATE, scope + " đã khóa.");
        }
        if (previous.isPresent() && !isLocked(previous.get(), branch, module)) {
            throw new BusinessException(ErrorCode.PERIOD_ORDER_VIOLATION,
                    "Phải khóa kỳ " + previous.get().label() + " trước khi khóa " + scope + ".");
        }
        List<String> blockers = closeBlockers(period, branch, module);
        if (!blockers.isEmpty()) {
            throw new BusinessException(ErrorCode.PERIOD_CLOSE_PRECONDITION,
                    "Chưa đủ điều kiện khóa " + scope + ": " + String.join("; ", blockers));
        }
        PeriodLockState before = PeriodLockState.of(lock);
        lock.lock(actor.id(), now, normalizeReason(reason));
        lockRepository.saveAndFlush(lock);
        auditLog.record(new AuditEntry(actor.id(), AuditActions.LOCK_PERIOD, AuditActions.FISCAL_PERIOD,
                period.getId(), null, lock.getReason(), before, PeriodLockState.of(lock), period.getCompanyId(),
                branch.getId()));
    }

    private void unlockOne(CurrentUser actor, FiscalPeriod period, Optional<FiscalPeriod> next, Branch branch,
                           PeriodModule module, String reason, Instant now) {
        String scope = describe(period, branch, module);
        PeriodLock lock = lockRepository.findForUpdate(period.getId(), branch.getId(), module)
                .filter(PeriodLock::isLocked)
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_PERIOD_STATE, scope + " đang mở."));
        if (next.isPresent() && isLocked(next.get(), branch, module)) {
            throw new BusinessException(ErrorCode.PERIOD_ORDER_VIOLATION,
                    "Phải mở lại kỳ " + next.get().label() + " trước khi mở " + scope + ".");
        }
        if (module == PeriodModule.INVENTORY && isLocked(period, branch, PeriodModule.ACCOUNTING)) {
            throw new BusinessException(ErrorCode.PERIOD_ORDER_VIOLATION,
                    "Phải mở lại kỳ kế toán " + period.label() + " trước khi mở " + scope + ".");
        }
        PeriodLockState before = PeriodLockState.of(lock);
        lock.unlock(actor.id(), now, reason);
        lockRepository.saveAndFlush(lock);
        auditLog.record(new AuditEntry(actor.id(), AuditActions.UNLOCK_PERIOD, AuditActions.FISCAL_PERIOD,
                period.getId(), null, reason, before, PeriodLockState.of(lock), period.getCompanyId(),
                branch.getId()));
    }

    /** BR-SYS-31: kỳ kế toán khóa sau kỳ kho; điều kiện khác do module nghiệp vụ cung cấp. */
    private List<String> closeBlockers(FiscalPeriod period, Branch branch, PeriodModule module) {
        List<String> blockers = new ArrayList<>();
        if (module == PeriodModule.ACCOUNTING && !isLocked(period, branch, PeriodModule.INVENTORY)) {
            blockers.add("kỳ kho " + period.label() + " chưa khóa");
        }
        PeriodCloseContext context = new PeriodCloseContext(period.getCompanyId(), branch.getId(), module,
                period.getStartDate(), period.getEndDate());
        closeChecks.orderedStream().forEach(check -> blockers.addAll(check.blockers(context)));
        return blockers;
    }

    private boolean isLocked(FiscalPeriod period, Branch branch, PeriodModule module) {
        return lockRepository.findForShare(period.getId(), branch.getId(), module)
                .map(PeriodLock::isLocked)
                .orElse(false);
    }

    /**
     * Tạo sẵn dòng trạng thái của kỳ đích và kỳ liền kề (mọi phân hệ), rồi khóa ghi hai kỳ. Sau bước này mọi
     * truy vấn khóa đều rơi vào dòng đã tồn tại.
     */
    private void prepare(FiscalPeriod period, Optional<FiscalPeriod> adjacent, List<Branch> branches) {
        List<Long> periodIds = new ArrayList<>();
        periodIds.add(period.getId());
        adjacent.ifPresent(other -> periodIds.add(other.getId()));
        initializer.ensureExists(periodIds, branches.stream().map(Branch::getId).toList());
        periodRepository.lockAllById(periodIds);
    }

    private Optional<FiscalPeriod> adjacent(FiscalPeriod period, int monthOffset) {
        YearMonth target = period.yearMonth().plusMonths(monthOffset);
        return periodRepository.findByCompanyIdAndYearAndMonth(period.getCompanyId(), target.getYear(),
                target.getMonthValue());
    }

    private FiscalPeriod findVisiblePeriod(CurrentUser actor, Long periodId) {
        FiscalPeriod period = periodRepository.findById(periodId).orElseThrow(BusinessException::notFound);
        if (!actor.dataScope().canAccessCompany(period.getCompanyId())) {
            throw BusinessException.notFound();
        }
        return period;
    }

    /** Chi nhánh ngoài phạm vi dữ liệu hoặc khác công ty của kỳ: 404 (BR-SYS-06). Trả theo thứ tự id. */
    private List<Branch> checkedBranches(CurrentUser actor, FiscalPeriod period, List<Long> branchIds) {
        TreeSet<Long> ids = new TreeSet<>(branchIds);
        if (!ids.stream().allMatch(actor.dataScope()::canAccessBranch)) {
            throw BusinessException.notFound();
        }
        Map<Long, Branch> branches = branchRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(Branch::getId, Function.identity()));
        if (branches.size() != ids.size()
                || branches.values().stream().anyMatch(b -> !b.getCompanyId().equals(period.getCompanyId()))) {
            throw BusinessException.notFound();
        }
        return ids.stream().map(branches::get).toList();
    }

    private static String describe(FiscalPeriod period, Branch branch, PeriodModule module) {
        return "kỳ " + PeriodModuleLabels.of(module) + " " + period.label() + " của chi nhánh " + branch.getCode();
    }

    private static String normalizeReason(String reason) {
        return reason == null || reason.isBlank() ? null : reason.trim();
    }

    /** Ảnh chụp trạng thái ghi vào nhật ký audit. */
    record PeriodLockState(PeriodModule module, Long branchId, boolean locked) {

        static PeriodLockState of(PeriodLock lock) {
            return new PeriodLockState(lock.getModule(), lock.getBranchId(), lock.isLocked());
        }
    }
}
