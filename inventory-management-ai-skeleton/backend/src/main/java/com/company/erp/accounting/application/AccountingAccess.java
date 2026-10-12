package com.company.erp.accounting.application;

import com.company.erp.common.application.AuditEntry;
import com.company.erp.common.application.AuditLogService;
import com.company.erp.common.domain.BusinessException;
import com.company.erp.common.domain.ErrorCode;
import com.company.erp.system.application.CurrentUser;
import org.springframework.stereotype.Component;

/** Kiểm tra dùng chung của module kế toán: quyền, phạm vi công ty / chi nhánh (ngoài phạm vi trả 404), audit. */
@Component
class AccountingAccess {

    static final String ENTITY_ACCOUNT = "ACCOUNT";
    static final String ENTITY_POSTING_RULE = "POSTING_RULE";

    private final AuditLogService auditLog;

    AccountingAccess(AuditLogService auditLog) {
        this.auditLog = auditLog;
    }

    void requirePermission(CurrentUser actor, String permission) {
        if (!actor.hasPermission(permission)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
    }

    void requireCompany(CurrentUser actor, Long companyId) {
        if (!actor.dataScope().canAccessCompany(companyId)) {
            throw BusinessException.notFound();
        }
    }

    boolean canAccessBranch(CurrentUser actor, Long companyId, Long branchId) {
        return actor.dataScope().canAccessCompany(companyId) && actor.dataScope().canAccessBranch(branchId);
    }

    void audit(CurrentUser actor, String action, String entityType, Long entityId, String reason, Object before,
               Object after, Long companyId) {
        auditLog.record(new AuditEntry(actor.id(), action, entityType, entityId, null, reason, before, after,
                companyId, null));
    }
}
