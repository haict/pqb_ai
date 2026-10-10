package com.company.erp.masterdata.application;

import com.company.erp.common.application.AuditEntry;
import com.company.erp.common.application.AuditLogService;
import com.company.erp.common.domain.BusinessException;
import com.company.erp.common.domain.ErrorCode;
import com.company.erp.masterdata.domain.CodeNameEntity;
import com.company.erp.system.application.CurrentUser;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * Kiểm tra dùng chung của module danh mục: phạm vi công ty (BR-SYS-06: ngoài phạm vi trả 404), trùng mã, audit.
 * Danh mục dùng chung toàn công ty nên người dùng thấy công ty là xem được; ghi cần quyền quản lý tương ứng.
 */
@Component
class MasterDataGuard {

    private final AuditLogService auditLog;

    MasterDataGuard(AuditLogService auditLog) {
        this.auditLog = auditLog;
    }

    void requireCompany(CurrentUser actor, Long companyId) {
        if (!actor.dataScope().canAccessCompany(companyId)) {
            throw BusinessException.notFound();
        }
    }

    <E extends CodeNameEntity> E visible(CurrentUser actor, Optional<E> entity) {
        E found = entity.orElseThrow(BusinessException::notFound);
        requireCompany(actor, found.getCompanyId());
        return found;
    }

    void requireNewCode(boolean exists, String label, String code) {
        if (exists) {
            throw new BusinessException(ErrorCode.DUPLICATE_CODE, label + " mã '" + code + "' đã tồn tại.");
        }
    }

    /** Tham chiếu danh mục khác phải tồn tại, cùng công ty, đang hoạt động. */
    <E extends CodeNameEntity> void requireActiveReference(Optional<E> entity, Long companyId, String field,
                                                           String label) {
        boolean valid = entity.filter(found -> found.getCompanyId().equals(companyId))
                .filter(CodeNameEntity::isActive)
                .isPresent();
        if (!valid) {
            throw BusinessException.invalidField(field, label + " không tồn tại hoặc đã ngừng hoạt động.");
        }
    }

    void audit(CurrentUser actor, String action, String entityType, Long entityId, String reason, Object before,
               Object after, Long companyId) {
        auditLog.record(new AuditEntry(actor.id(), action, entityType, entityId, null, reason, before, after,
                companyId, null));
    }
}
