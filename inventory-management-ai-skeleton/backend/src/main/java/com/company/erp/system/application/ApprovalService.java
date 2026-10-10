package com.company.erp.system.application;

import com.company.erp.common.domain.BusinessException;
import com.company.erp.common.domain.ErrorCode;
import com.company.erp.common.domain.RecordStatus;
import com.company.erp.system.application.dto.ApprovalRequestResponse;
import com.company.erp.system.domain.ApprovalAction;
import com.company.erp.system.domain.ApprovalActionType;
import com.company.erp.system.domain.ApprovalMode;
import com.company.erp.system.domain.ApprovalRequest;
import com.company.erp.system.domain.ApprovalRequestStatus;
import com.company.erp.system.domain.ApprovalWorkflow;
import com.company.erp.system.domain.DocumentTypeSetting;
import com.company.erp.system.infrastructure.persistence.ApprovalActionRepository;
import com.company.erp.system.infrastructure.persistence.ApprovalRequestRepository;
import com.company.erp.system.infrastructure.persistence.ApprovalWorkflowRepository;
import com.company.erp.system.infrastructure.persistence.DocumentTypeSettingRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Bộ máy phê duyệt dùng chung (BR-SYS-11, BR-GEN-05, BR-SYS-14, D-04). Module sở hữu chứng từ gọi trong transaction
 * của lệnh gửi duyệt / duyệt / từ chối / hủy, sau khi đã khóa chứng từ và kiểm tra trạng thái; khi yêu cầu hoàn tất,
 * module tự chuyển trạng thái chứng từ. Endpoint hành động nằm ở module sở hữu ({@code POST /{resource}/{id}/approve}).
 */
@Service
public class ApprovalService {

    private static final String CATEGORY = "APPROVAL";

    private final DocumentTypeSettingRepository typeRepository;
    private final ApprovalWorkflowRepository workflowRepository;
    private final ApprovalRequestRepository requestRepository;
    private final ApprovalActionRepository actionRepository;
    private final ApproverResolver approverResolver;
    private final ApprovalResponseMapper mapper;
    private final NotificationService notifications;
    private final Clock clock;

    public ApprovalService(DocumentTypeSettingRepository typeRepository, ApprovalWorkflowRepository workflowRepository,
                           ApprovalRequestRepository requestRepository, ApprovalActionRepository actionRepository,
                           ApproverResolver approverResolver, ApprovalResponseMapper mapper,
                           NotificationService notifications, Clock clock) {
        this.typeRepository = typeRepository;
        this.workflowRepository = workflowRepository;
        this.requestRepository = requestRepository;
        this.actionRepository = actionRepository;
        this.approverResolver = approverResolver;
        this.mapper = mapper;
        this.notifications = notifications;
        this.clock = clock;
    }

    /**
     * Gửi duyệt. Không cần duyệt (chế độ NONE, hoặc CONFIGURED mà không có luồng khớp): trả {@code approvalRequired =
     * false}. Chế độ REQUIRED mà không có luồng khớp: 422 {@code APPROVAL_NOT_CONFIGURED}.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public ApprovalSubmitResult submit(CurrentUser actor, ApprovalSubmission submission) {
        DocumentTypeSetting type = documentType(submission.documentType());
        if (type.getApprovalMode() == ApprovalMode.NONE) {
            return ApprovalSubmitResult.notRequired();
        }
        Optional<ApprovalWorkflow> workflow = selectWorkflow(submission);
        if (workflow.isEmpty()) {
            if (type.getApprovalMode() == ApprovalMode.REQUIRED) {
                throw new BusinessException(ErrorCode.APPROVAL_NOT_CONFIGURED,
                        "Chưa cấu hình luồng duyệt cho \"" + type.getName() + "\" áp dụng với chứng từ này.");
            }
            return ApprovalSubmitResult.notRequired();
        }
        Instant now = Instant.now(clock);
        ApprovalRequest request = saveNew(new ApprovalRequest(submission.companyId(), submission.branchId(),
                submission.documentType(), submission.documentId(), submission.documentNo(), submission.amount(),
                submission.discountPercent(), workflow.get(), actor.id(), now));
        actionRepository.save(new ApprovalAction(request.getId(), null, ApprovalActionType.SUBMIT, actor.id(), null,
                null, now));
        notifyApprovers(request, type);
        return new ApprovalSubmitResult(true, request.getId());
    }

    /**
     * Duyệt bước hiện tại. Người lập không tự duyệt khi loại chứng từ không cho phép (BR-SYS-14); một người không duyệt
     * hai bước của cùng một yêu cầu.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public ApprovalStepResult approve(CurrentUser actor, String documentType, Long documentId, String comment) {
        ApprovalRequest request = pendingRequest(documentType, documentId);
        DocumentTypeSetting type = documentType(documentType);
        ApproverResolver.Authorization authorization = approverResolver.authorize(actor, request);
        requireSeparationOfDuties(actor, request, type, authorization);
        requireNotApprovedEarlierStep(actor, request);
        Instant now = Instant.now(clock);
        int step = request.getCurrentStep();
        int position = request.currentStepPosition();
        boolean completed = request.approveCurrentStep(now);
        actionRepository.save(new ApprovalAction(request.getId(), step, ApprovalActionType.APPROVE, actor.id(),
                authorization.onBehalfOf(), normalize(comment), now));
        if (completed) {
            notifyRequester(request, type, "đã được duyệt", "APPROVED");
        } else {
            notifyApprovers(request, type);
        }
        return new ApprovalStepResult(request.getId(), completed, position, request.getTotalSteps());
    }

    /** Từ chối: kết thúc yêu cầu; module chuyển chứng từ sang REJECTED. Bắt buộc lý do. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void reject(CurrentUser actor, String documentType, Long documentId, String reason) {
        if (reason == null || reason.isBlank()) {
            throw BusinessException.invalidField("reason", "Bắt buộc nhập lý do từ chối.");
        }
        ApprovalRequest request = pendingRequest(documentType, documentId);
        DocumentTypeSetting type = documentType(documentType);
        ApproverResolver.Authorization authorization = approverResolver.authorize(actor, request);
        requireSeparationOfDuties(actor, request, type, authorization);
        Instant now = Instant.now(clock);
        int step = request.getCurrentStep();
        request.reject(now);
        actionRepository.save(new ApprovalAction(request.getId(), step, ApprovalActionType.REJECT, actor.id(),
                authorization.onBehalfOf(), reason.trim(), now));
        notifyRequester(request, type, "bị từ chối: " + reason.trim(), "REJECTED");
    }

    /** Hủy yêu cầu đang chờ khi chứng từ bị hủy / rút lại. Không có yêu cầu đang chờ thì bỏ qua. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void cancelPending(CurrentUser actor, String documentType, Long documentId, String reason) {
        lockPending(documentType, documentId).ifPresent(request -> {
            Instant now = Instant.now(clock);
            request.cancel(now);
            actionRepository.save(new ApprovalAction(request.getId(), request.getCurrentStep(),
                    ApprovalActionType.CANCEL, actor.id(), null, normalize(reason), now));
        });
    }

    /** Lịch sử duyệt của một chứng từ (mới nhất trước), để module hiển thị trên trang chi tiết chứng từ. */
    @Transactional(readOnly = true)
    public List<ApprovalRequestResponse> history(String documentType, Long documentId) {
        return mapper.toResponses(
                requestRepository.findByDocumentTypeAndDocumentIdOrderByRequestedAtDescIdDesc(documentType, documentId));
    }

    private Optional<ApprovalWorkflow> selectWorkflow(ApprovalSubmission submission) {
        return workflowRepository.findByCompanyIdAndDocumentTypeAndStatus(submission.companyId(),
                        submission.documentType(), RecordStatus.ACTIVE).stream()
                .filter(workflow -> workflow.matches(submission.branchId(), submission.amount(),
                        submission.discountPercent()))
                .min(Comparator.comparingInt(ApprovalWorkflow::getPriority).reversed()
                        .thenComparing(Comparator.comparingInt(ApprovalWorkflow::specificity).reversed())
                        .thenComparing(ApprovalWorkflow::getId));
    }

    private ApprovalRequest saveNew(ApprovalRequest request) {
        try {
            return requestRepository.saveAndFlush(request);
        } catch (DataIntegrityViolationException ex) {
            throw new BusinessException(ErrorCode.INVALID_DOCUMENT_STATE, "Chứng từ đang chờ duyệt.");
        }
    }

    private ApprovalRequest pendingRequest(String documentType, Long documentId) {
        return lockPending(documentType, documentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_DOCUMENT_STATE,
                        "Chứng từ không có yêu cầu duyệt đang chờ."));
    }

    /** Khóa yêu cầu đang chờ theo khóa chính; người khác vừa xử lý xong thì không còn đang chờ. */
    private Optional<ApprovalRequest> lockPending(String documentType, Long documentId) {
        return requestRepository.findPendingId(documentType, documentId)
                .flatMap(requestRepository::findByIdForUpdate)
                .filter(request -> request.getStatus() == ApprovalRequestStatus.PENDING);
    }

    private DocumentTypeSetting documentType(String code) {
        return typeRepository.findById(code)
                .orElseThrow(() -> new IllegalStateException("Loại chứng từ chưa được đăng ký: " + code));
    }

    private static void requireSeparationOfDuties(CurrentUser actor, ApprovalRequest request, DocumentTypeSetting type,
                                                  ApproverResolver.Authorization authorization) {
        boolean selfApproval = request.getRequestedBy().equals(actor.id())
                || request.getRequestedBy().equals(authorization.onBehalfOf());
        if (selfApproval && !type.isSelfApprovalAllowed()) {
            throw new BusinessException(ErrorCode.SELF_APPROVAL_NOT_ALLOWED);
        }
    }

    private void requireNotApprovedEarlierStep(CurrentUser actor, ApprovalRequest request) {
        boolean approvedBefore = actionRepository.findByRequestIdInOrderByActedAtAscIdAsc(Set.of(request.getId()))
                .stream()
                .anyMatch(action -> action.getAction() == ApprovalActionType.APPROVE
                        && action.getActorUserId().equals(actor.id()));
        if (approvedBefore) {
            throw new BusinessException(ErrorCode.FORBIDDEN,
                    "Bạn đã duyệt một bước của chứng từ này; bước tiếp theo phải do người khác duyệt.");
        }
    }

    private void notifyApprovers(ApprovalRequest request, DocumentTypeSetting type) {
        Set<Long> approvers = approverResolver.currentApproverIds(request);
        if (approvers.isEmpty()) {
            return;
        }
        notifications.notify(approvers, new NotificationMessage(
                "APPROVAL:" + request.getId() + ":STEP:" + request.getCurrentStep(), CATEGORY,
                "Chờ duyệt: " + type.getName() + " " + nullToEmpty(request.getDocumentNo()),
                "Bước " + request.currentStepPosition() + "/" + request.getTotalSteps() + " đang chờ bạn duyệt.",
                request.getDocumentType(), request.getDocumentId()));
    }

    private void notifyRequester(ApprovalRequest request, DocumentTypeSetting type, String outcome, String event) {
        notifications.notify(Set.of(request.getRequestedBy()), new NotificationMessage(
                "APPROVAL:" + request.getId() + ":" + event, CATEGORY,
                type.getName() + " " + nullToEmpty(request.getDocumentNo()) + " " + outcome, null,
                request.getDocumentType(), request.getDocumentId()));
    }

    private static String normalize(String text) {
        return text == null || text.isBlank() ? null : text.trim();
    }

    private static String nullToEmpty(String text) {
        return text == null ? "" : text;
    }
}
