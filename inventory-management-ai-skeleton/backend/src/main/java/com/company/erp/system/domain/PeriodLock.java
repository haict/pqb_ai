package com.company.erp.system.domain;

import com.company.erp.common.domain.AuditableEntity;
import com.company.erp.common.domain.BusinessException;
import com.company.erp.common.domain.ErrorCode;
import com.company.erp.common.domain.PeriodModule;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * Trạng thái khóa của một kỳ cho một chi nhánh và một phân hệ (BR-SYS-30). Lần khóa/mở gần nhất lưu ở đây;
 * toàn bộ lịch sử ở nhật ký audit (BR-SYS-21).
 */
@Entity
@Table(name = "period_locks")
public class PeriodLock extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "fiscal_period_id", nullable = false, updatable = false)
    private Long fiscalPeriodId;

    @Column(name = "branch_id", nullable = false, updatable = false)
    private Long branchId;

    @Enumerated(EnumType.STRING)
    @Column(name = "module", nullable = false, length = 20, updatable = false)
    private PeriodModule module;

    @Column(name = "locked", nullable = false)
    private boolean locked;

    @Column(name = "changed_by")
    private Long changedBy;

    @Column(name = "changed_at")
    private Instant changedAt;

    @Column(name = "reason", length = 500)
    private String reason;

    protected PeriodLock() {
    }

    public void lock(Long actorId, Instant now, String reason) {
        if (locked) {
            throw new BusinessException(ErrorCode.INVALID_PERIOD_STATE, "Kỳ đã khóa.");
        }
        change(true, actorId, now, reason);
    }

    public void unlock(Long actorId, Instant now, String reason) {
        if (!locked) {
            throw new BusinessException(ErrorCode.INVALID_PERIOD_STATE, "Kỳ đang mở.");
        }
        change(false, actorId, now, reason);
    }

    private void change(boolean locked, Long actorId, Instant now, String reason) {
        this.locked = locked;
        this.changedBy = actorId;
        this.changedAt = now;
        this.reason = reason;
    }

    public Long getId() {
        return id;
    }

    public Long getFiscalPeriodId() {
        return fiscalPeriodId;
    }

    public Long getBranchId() {
        return branchId;
    }

    public PeriodModule getModule() {
        return module;
    }

    public boolean isLocked() {
        return locked;
    }

    public Long getChangedBy() {
        return changedBy;
    }

    public Instant getChangedAt() {
        return changedAt;
    }

    public String getReason() {
        return reason;
    }
}
