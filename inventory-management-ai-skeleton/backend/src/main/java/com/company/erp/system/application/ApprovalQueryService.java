package com.company.erp.system.application;

import com.company.erp.common.domain.BusinessException;
import com.company.erp.system.application.dto.ApprovalRequestResponse;
import com.company.erp.system.domain.ApprovalRequest;
import com.company.erp.system.domain.ApprovalRequestStatus;
import com.company.erp.system.domain.DocumentTypeSetting;
import com.company.erp.system.infrastructure.persistence.ApprovalActionRepository;
import com.company.erp.system.infrastructure.persistence.ApprovalRequestRepository;
import com.company.erp.system.infrastructure.persistence.DocumentTypeSettingRepository;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Việc chờ duyệt của tôi và chi tiết yêu cầu duyệt (docs/04 M01). */
@Service
public class ApprovalQueryService {

    private static final String VIEW_ALL_PERMISSION = "approval-workflow:view";

    private final ApprovalRequestRepository requestRepository;
    private final ApprovalActionRepository actionRepository;
    private final DocumentTypeSettingRepository typeRepository;
    private final ApproverResolver approverResolver;
    private final ApprovalResponseMapper mapper;

    public ApprovalQueryService(ApprovalRequestRepository requestRepository, ApprovalActionRepository actionRepository,
                                DocumentTypeSettingRepository typeRepository, ApproverResolver approverResolver,
                                ApprovalResponseMapper mapper) {
        this.requestRepository = requestRepository;
        this.actionRepository = actionRepository;
        this.typeRepository = typeRepository;
        this.approverResolver = approverResolver;
        this.mapper = mapper;
    }

    /**
     * Yêu cầu đang chờ người dùng duyệt (của chính mình hoặc duyệt thay theo ủy quyền), mới nhất trước. Bỏ chứng từ do
     * chính mình lập khi loại chứng từ không cho tự duyệt. Số yêu cầu đang chờ nhỏ nên phân trang trong bộ nhớ.
     */
    @Transactional(readOnly = true)
    public Page<ApprovalRequestResponse> pending(CurrentUser actor, Pageable pageable) {
        Map<Long, Optional<Long>> pending = approverResolver.pendingFor(actor);
        Map<String, DocumentTypeSetting> types = typeRepository.findAll().stream()
                .collect(Collectors.toMap(DocumentTypeSetting::getCode, Function.identity()));
        List<ApprovalRequest> requests = requestRepository.findAllById(pending.keySet()).stream()
                .filter(request -> request.getStatus() == ApprovalRequestStatus.PENDING)
                .filter(request -> !isBlockedSelfApproval(actor, request, pending.get(request.getId()),
                        types.get(request.getDocumentType())))
                .sorted(Comparator.comparing(ApprovalRequest::getRequestedAt).reversed()
                        .thenComparing(ApprovalRequest::getId, Comparator.reverseOrder()))
                .toList();
        int from = (int) Math.min(pageable.getOffset(), requests.size());
        int to = Math.min(from + pageable.getPageSize(), requests.size());
        return new PageImpl<>(mapper.toResponses(requests.subList(from, to)), pageable, requests.size());
    }

    /** Xem được khi là người gửi, người đã xử lý, người đang được chờ duyệt, hoặc có quyền xem luồng duyệt và phạm vi. */
    @Transactional(readOnly = true)
    public ApprovalRequestResponse get(CurrentUser actor, Long id) {
        ApprovalRequest request = requestRepository.findById(id).orElseThrow(BusinessException::notFound);
        if (!canView(actor, request)) {
            throw BusinessException.notFound();
        }
        return mapper.toResponses(List.of(request)).get(0);
    }

    private boolean canView(CurrentUser actor, ApprovalRequest request) {
        if (request.getRequestedBy().equals(actor.id())) {
            return true;
        }
        if (actor.hasPermission(VIEW_ALL_PERMISSION) && actor.dataScope().canAccessBranch(request.getBranchId())) {
            return true;
        }
        boolean acted = actionRepository.findByRequestIdInOrderByActedAtAscIdAsc(Set.of(request.getId())).stream()
                .anyMatch(action -> action.getActorUserId().equals(actor.id()));
        return acted || approverResolver.pendingFor(actor).containsKey(request.getId());
    }

    private static boolean isBlockedSelfApproval(CurrentUser actor, ApprovalRequest request,
                                                 Optional<Long> onBehalfOf, DocumentTypeSetting type) {
        if (type == null || type.isSelfApprovalAllowed()) {
            return false;
        }
        return request.getRequestedBy().equals(actor.id())
                || onBehalfOf.map(request.getRequestedBy()::equals).orElse(false);
    }
}
