package com.company.erp.system.application;

import com.company.erp.common.api.PageRequests;
import com.company.erp.common.application.AuditEntry;
import com.company.erp.common.application.AuditLogService;
import com.company.erp.common.application.BusinessCalendar;
import com.company.erp.common.application.IdempotencyService;
import com.company.erp.common.application.IdempotentCommand;
import com.company.erp.common.domain.BusinessException;
import com.company.erp.common.domain.DocumentNumberFormat;
import com.company.erp.common.domain.DocumentNumberPattern;
import com.company.erp.common.domain.ErrorCode;
import com.company.erp.common.domain.NumberResetCycle;
import com.company.erp.common.infrastructure.persistence.DocumentNumberFormatRepository;
import com.company.erp.common.infrastructure.persistence.DocumentSequenceRepository;
import com.company.erp.common.infrastructure.persistence.Specs;
import com.company.erp.system.application.dto.DocumentNumberFormatResponse;
import com.company.erp.system.application.dto.DocumentNumberFormatStatus;
import com.company.erp.system.application.dto.ScheduleDocumentNumberFormatRequest;
import com.company.erp.system.infrastructure.persistence.CompanyRepository;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Cấu hình số chứng từ (BR-SYS-10, D-03). Loại chứng từ đã cấp số: thay đổi chỉ có hiệu lực từ chu kỳ sau, nên
 * chu kỳ đang chạy giữ nguyên định dạng và bộ đếm, không sinh số trùng. Chưa cấp số: hiệu lực ngay.
 */
@Service
public class DocumentNumberFormatService {

    private static final Set<String> SORTABLE = Set.of("documentType", "effectiveFrom", "updatedAt");
    private static final String ENDPOINT_SCHEDULE = "POST /api/v1/document-number-formats";
    /** Ngày hiệu lực của cấu hình đặt trước khi loại chứng từ được cấp số lần đầu: áp dụng cho mọi ngày. */
    private static final LocalDate EFFECTIVE_FROM_BEGINNING = LocalDate.of(2000, 1, 1);

    private final DocumentNumberFormatRepository formatRepository;
    private final DocumentSequenceRepository sequenceRepository;
    private final CompanyRepository companyRepository;
    private final BusinessCalendar calendar;
    private final IdempotencyService idempotency;
    private final AuditLogService auditLog;

    public DocumentNumberFormatService(DocumentNumberFormatRepository formatRepository,
                                       DocumentSequenceRepository sequenceRepository,
                                       CompanyRepository companyRepository, BusinessCalendar calendar,
                                       IdempotencyService idempotency, AuditLogService auditLog) {
        this.formatRepository = formatRepository;
        this.sequenceRepository = sequenceRepository;
        this.companyRepository = companyRepository;
        this.calendar = calendar;
        this.idempotency = idempotency;
        this.auditLog = auditLog;
    }

    @Transactional(readOnly = true)
    public Page<DocumentNumberFormatResponse> search(CurrentUser actor, Long companyId, String documentType,
                                                     Pageable pageable) {
        requireVisibleCompany(actor, companyId);
        LocalDate today = calendar.today();
        Map<String, Optional<Long>> currentIds = new HashMap<>();
        return formatRepository.findAll(Specs.allOf(
                        Specs.<DocumentNumberFormat>equalsIfPresent("companyId", companyId),
                        Specs.keyword(documentType, "documentType")),
                PageRequests.checked(pageable, SORTABLE,
                        Sort.by(Sort.Order.asc("documentType"), Sort.Order.desc("effectiveFrom"))))
                .map(format -> toResponse(format, status(format, today, currentIds)));
    }

    @Transactional
    public DocumentNumberFormatResponse schedule(CurrentUser actor, String idempotencyKey,
                                                 ScheduleDocumentNumberFormatRequest request) {
        IdempotentCommand command = new IdempotentCommand(actor.id(), idempotencyKey, ENDPOINT_SCHEDULE, request);
        return idempotency.execute(command, DocumentNumberFormatResponse.class, () -> {
            Long companyId = request.companyId();
            requireFullCompanyAccess(actor, companyId);
            DocumentNumberPattern pattern = new DocumentNumberPattern(request.prefix(), request.sequenceLength(),
                    request.resetCycle());
            LocalDate today = calendar.today();
            LocalDate effectiveFrom = effectiveFrom(companyId, request.documentType(), pattern, today);
            Optional<DocumentNumberFormat> existing = formatRepository
                    .findByCompanyIdAndDocumentTypeAndEffectiveFrom(companyId, request.documentType(), effectiveFrom);
            Object before = existing.map(format -> toResponse(format, null)).orElse(null);
            DocumentNumberFormat format = existing
                    .map(current -> {
                        current.change(pattern);
                        return current;
                    })
                    .orElseGet(() -> new DocumentNumberFormat(companyId, request.documentType(), pattern,
                            effectiveFrom));
            format = formatRepository.saveAndFlush(format);
            DocumentNumberFormatResponse after = toResponse(format,
                    effectiveFrom.isAfter(today) ? DocumentNumberFormatStatus.SCHEDULED
                            : DocumentNumberFormatStatus.CURRENT);
            auditLog.record(new AuditEntry(actor.id(), existing.isPresent() ? AuditActions.UPDATE : AuditActions.CREATE,
                    AuditActions.DOCUMENT_NUMBER_FORMAT, format.getId(), null, null, before, after, companyId, null));
            return after;
        });
    }

    /** Xóa cấu hình chưa có hiệu lực (hủy thay đổi đã đặt trước). */
    @Transactional
    public void deleteScheduled(CurrentUser actor, Long id) {
        DocumentNumberFormat format = formatRepository.findById(id).orElseThrow(BusinessException::notFound);
        requireFullCompanyAccess(actor, format.getCompanyId());
        if (!format.getEffectiveFrom().isAfter(calendar.today())) {
            throw new BusinessException(ErrorCode.CONFIGURATION_IN_EFFECT);
        }
        DocumentNumberFormatResponse before = toResponse(format, DocumentNumberFormatStatus.SCHEDULED);
        formatRepository.delete(format);
        auditLog.record(new AuditEntry(actor.id(), AuditActions.DELETE, AuditActions.DOCUMENT_NUMBER_FORMAT, id,
                null, null, before, null, format.getCompanyId(), null));
    }

    private LocalDate effectiveFrom(Long companyId, String documentType, DocumentNumberPattern pattern,
                                    LocalDate today) {
        if (!sequenceRepository.existsByCompanyIdAndDocumentType(companyId, documentType)) {
            return EFFECTIVE_FROM_BEGINNING;
        }
        NumberResetCycle currentCycle = formatRepository
                .findFirstByCompanyIdAndDocumentTypeAndEffectiveFromLessThanEqualOrderByEffectiveFromDesc(
                        companyId, documentType, today)
                .map(format -> format.pattern().resetCycle())
                .orElse(DocumentNumberPattern.DEFAULT_RESET_CYCLE);
        return DocumentNumberPattern.nextCycleStart(currentCycle, pattern.resetCycle(), today);
    }

    private DocumentNumberFormatStatus status(DocumentNumberFormat format, LocalDate today,
                                              Map<String, Optional<Long>> currentIds) {
        if (format.getEffectiveFrom().isAfter(today)) {
            return DocumentNumberFormatStatus.SCHEDULED;
        }
        Optional<Long> currentId = currentIds.computeIfAbsent(format.getDocumentType(), type -> formatRepository
                .findFirstByCompanyIdAndDocumentTypeAndEffectiveFromLessThanEqualOrderByEffectiveFromDesc(
                        format.getCompanyId(), type, today)
                .map(DocumentNumberFormat::getId));
        return currentId.filter(format.getId()::equals).isPresent()
                ? DocumentNumberFormatStatus.CURRENT
                : DocumentNumberFormatStatus.SUPERSEDED;
    }

    private void requireVisibleCompany(CurrentUser actor, Long companyId) {
        if (!actor.dataScope().canAccessCompany(companyId) || !companyRepository.existsById(companyId)) {
            throw BusinessException.notFound();
        }
    }

    private void requireFullCompanyAccess(CurrentUser actor, Long companyId) {
        requireVisibleCompany(actor, companyId);
        if (!actor.dataScope().hasFullCompanyAccess(companyId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN,
                    "Cần phạm vi dữ liệu toàn công ty để cấu hình số chứng từ.");
        }
    }

    private static DocumentNumberFormatResponse toResponse(DocumentNumberFormat format,
                                                           DocumentNumberFormatStatus status) {
        DocumentNumberPattern pattern = format.pattern();
        return new DocumentNumberFormatResponse(format.getId(), format.getCompanyId(), format.getDocumentType(),
                pattern.prefix(), pattern.sequenceLength(), pattern.resetCycle(), format.getEffectiveFrom(), status,
                format.getVersion());
    }
}
