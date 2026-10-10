package com.company.erp.system.application;

import com.company.erp.common.api.PageRequests;
import com.company.erp.common.application.AuditEntry;
import com.company.erp.common.application.AuditLogService;
import com.company.erp.common.application.IdempotencyService;
import com.company.erp.common.application.IdempotentCommand;
import com.company.erp.common.domain.BusinessException;
import com.company.erp.common.domain.ErrorCode;
import com.company.erp.system.application.dto.CreateDelegationRequest;
import com.company.erp.system.application.dto.DelegationResponse;
import com.company.erp.system.domain.ApprovalDelegation;
import com.company.erp.system.domain.User;
import com.company.erp.system.infrastructure.persistence.ApprovalDelegationRepository;
import com.company.erp.system.infrastructure.persistence.DocumentTypeSettingRepository;
import com.company.erp.system.infrastructure.persistence.UserRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Ủy quyền duyệt có thời hạn (BR-SYS-11). Người dùng ủy quyền phần duyệt của chính mình; ủy quyền thay người khác
 * hoặc xem mọi ủy quyền của công ty cần quyền {@code approval-workflow:manage}.
 */
@Service
public class ApprovalDelegationService {

    private static final String MANAGE_PERMISSION = "approval-workflow:manage";
    private static final Set<String> SORTABLE = Set.of("validFrom", "validTo", "createdAt");
    private static final String ENDPOINT_CREATE = "POST /api/v1/approval-delegations";

    private final ApprovalDelegationRepository repository;
    private final UserRepository userRepository;
    private final DocumentTypeSettingRepository typeRepository;
    private final IdempotencyService idempotency;
    private final AuditLogService auditLog;
    private final Clock clock;

    public ApprovalDelegationService(ApprovalDelegationRepository repository, UserRepository userRepository,
                                     DocumentTypeSettingRepository typeRepository, IdempotencyService idempotency,
                                     AuditLogService auditLog, Clock clock) {
        this.repository = repository;
        this.userRepository = userRepository;
        this.typeRepository = typeRepository;
        this.idempotency = idempotency;
        this.auditLog = auditLog;
        this.clock = clock;
    }

    /** Ủy quyền liên quan đến tôi (ủy quyền đi hoặc được ủy quyền); người quản lý thấy mọi ủy quyền của công ty. */
    @Transactional(readOnly = true)
    public Page<DelegationResponse> search(CurrentUser actor, Pageable pageable) {
        Specification<ApprovalDelegation> scope = actor.hasPermission(MANAGE_PERMISSION)
                ? (root, query, cb) -> root.get("companyId").in(actor.dataScope().fullCompanyIds().isEmpty()
                        ? Set.of(actor.companyId()) : actor.dataScope().fullCompanyIds())
                : (root, query, cb) -> cb.or(cb.equal(root.get("delegatorUserId"), actor.id()),
                        cb.equal(root.get("delegateUserId"), actor.id()));
        Page<ApprovalDelegation> page = repository.findAll(scope,
                PageRequests.checked(pageable, SORTABLE, Sort.by(Sort.Order.desc("validFrom"))));
        Map<Long, String> names = userRepository.findAllById(page.getContent().stream()
                        .flatMap(delegation -> Stream.of(delegation.getDelegatorUserId(),
                                delegation.getDelegateUserId()))
                        .collect(Collectors.toSet())).stream()
                .collect(Collectors.toMap(User::getId, User::getFullName));
        Instant now = Instant.now(clock);
        return page.map(delegation -> toResponse(delegation, names, now));
    }

    @Transactional
    public DelegationResponse create(CurrentUser actor, String idempotencyKey, CreateDelegationRequest request) {
        IdempotentCommand command = new IdempotentCommand(actor.id(), idempotencyKey, ENDPOINT_CREATE, request);
        return idempotency.execute(command, DelegationResponse.class, () -> {
            Long delegatorId = request.delegatorUserId() == null ? actor.id() : request.delegatorUserId();
            if (!delegatorId.equals(actor.id()) && !actor.hasPermission(MANAGE_PERMISSION)) {
                throw new BusinessException(ErrorCode.FORBIDDEN, "Chỉ được ủy quyền phần duyệt của chính mình.");
            }
            User delegator = activeUser(delegatorId, "delegatorUserId");
            if (!actor.dataScope().canAccessCompany(delegator.getCompanyId())) {
                throw BusinessException.notFound();
            }
            User delegate = activeUser(request.delegateUserId(), "delegateUserId");
            if (!delegate.getCompanyId().equals(delegator.getCompanyId())) {
                throw BusinessException.invalidField("delegateUserId", "Người được ủy quyền phải cùng công ty.");
            }
            if (request.documentType() != null && !typeRepository.existsById(request.documentType())) {
                throw BusinessException.invalidField("documentType", "Loại chứng từ không tồn tại.");
            }
            Instant now = Instant.now(clock);
            Instant validFrom = request.validFrom() == null ? now : request.validFrom();
            if (!request.validTo().isAfter(now)) {
                throw BusinessException.invalidField("validTo", "Thời điểm kết thúc phải ở tương lai.");
            }
            ApprovalDelegation delegation = repository.saveAndFlush(new ApprovalDelegation(delegator.getCompanyId(),
                    delegator.getId(), delegate.getId(), request.documentType(), validFrom, request.validTo(),
                    trimToNull(request.reason())));
            DelegationResponse response = toResponse(delegation,
                    Map.of(delegator.getId(), delegator.getFullName(), delegate.getId(), delegate.getFullName()), now);
            auditLog.record(new AuditEntry(actor.id(), AuditActions.CREATE, AuditActions.APPROVAL_DELEGATION,
                    delegation.getId(), null, delegation.getReason(), null, response, delegation.getCompanyId(), null));
            return response;
        });
    }

    /** Thu hồi: người ủy quyền hoặc người quản lý luồng duyệt. */
    @Transactional
    public void revoke(CurrentUser actor, Long id) {
        ApprovalDelegation delegation = repository.findById(id)
                .filter(found -> actor.dataScope().canAccessCompany(found.getCompanyId()))
                .orElseThrow(BusinessException::notFound);
        boolean owner = delegation.getDelegatorUserId().equals(actor.id());
        if (!owner && !actor.hasPermission(MANAGE_PERMISSION)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "Chỉ người ủy quyền được thu hồi ủy quyền này.");
        }
        delegation.revoke();
        repository.saveAndFlush(delegation);
        auditLog.record(new AuditEntry(actor.id(), AuditActions.REVOKE, AuditActions.APPROVAL_DELEGATION, id, null,
                null, null, null, delegation.getCompanyId(), null));
    }

    private User activeUser(Long id, String field) {
        return userRepository.findById(id).filter(User::isActive)
                .orElseThrow(() -> BusinessException.invalidField(field, "Người dùng không tồn tại hoặc đã bị khóa."));
    }

    private static DelegationResponse toResponse(ApprovalDelegation delegation, Map<Long, String> names, Instant now) {
        return new DelegationResponse(delegation.getId(), delegation.getCompanyId(), delegation.getDelegatorUserId(),
                names.get(delegation.getDelegatorUserId()), delegation.getDelegateUserId(),
                names.get(delegation.getDelegateUserId()), delegation.getDocumentType(), delegation.getValidFrom(),
                delegation.getValidTo(), delegation.getStatus(), delegation.isEffective(now, delegation.getDocumentType()),
                delegation.getReason());
    }

    private static String trimToNull(String text) {
        return text == null || text.isBlank() ? null : text.trim();
    }
}
