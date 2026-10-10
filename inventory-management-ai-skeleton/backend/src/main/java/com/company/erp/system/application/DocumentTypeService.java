package com.company.erp.system.application;

import com.company.erp.common.application.AuditEntry;
import com.company.erp.common.application.AuditLogService;
import com.company.erp.common.domain.BusinessException;
import com.company.erp.common.domain.ErrorCode;
import com.company.erp.system.application.dto.DocumentTypeResponse;
import com.company.erp.system.application.dto.UpdateDocumentTypeRequest;
import com.company.erp.system.domain.DocumentTypeSetting;
import com.company.erp.system.infrastructure.persistence.DocumentTypeSettingRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Danh mục loại chứng từ: chế độ duyệt (BR-GEN-05) và cho phép người lập tự duyệt (BR-SYS-14). Cấu hình dùng chung
 * mọi công ty (P1 vận hành một công ty, D-23). Danh mục nhỏ, không phân trang.
 */
@Service
public class DocumentTypeService {

    private final DocumentTypeSettingRepository repository;
    private final AuditLogService auditLog;
    private final Clock clock;

    public DocumentTypeService(DocumentTypeSettingRepository repository, AuditLogService auditLog, Clock clock) {
        this.repository = repository;
        this.auditLog = auditLog;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<DocumentTypeResponse> list() {
        return repository.findAllByOrderBySortOrderAscCodeAsc().stream().map(DocumentTypeService::toResponse).toList();
    }

    @Transactional
    public DocumentTypeResponse update(CurrentUser actor, String code, UpdateDocumentTypeRequest request) {
        if (actor.dataScope().fullCompanyIds().isEmpty()) {
            throw new BusinessException(ErrorCode.FORBIDDEN,
                    "Cần phạm vi dữ liệu toàn công ty để cấu hình loại chứng từ.");
        }
        DocumentTypeSetting type = repository.findById(code).orElseThrow(BusinessException::notFound);
        if (type.getVersion() != request.version()) {
            throw new BusinessException(ErrorCode.CONCURRENT_MODIFICATION);
        }
        DocumentTypeResponse before = toResponse(type);
        type.configure(request.approvalMode(), request.selfApprovalAllowed(), actor.id(), Instant.now(clock));
        DocumentTypeResponse after = toResponse(repository.saveAndFlush(type));
        auditLog.record(new AuditEntry(actor.id(), AuditActions.UPDATE, AuditActions.DOCUMENT_TYPE, null, code, null,
                before, after, null, null));
        return after;
    }

    private static DocumentTypeResponse toResponse(DocumentTypeSetting type) {
        return new DocumentTypeResponse(type.getCode(), type.getModule(), type.getName(), type.getApprovalMode(),
                type.isSelfApprovalAllowed(), type.getVersion(), type.getUpdatedAt());
    }
}
