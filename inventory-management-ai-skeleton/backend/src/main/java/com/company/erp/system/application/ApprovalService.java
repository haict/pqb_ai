package com.company.erp.system.application;

import com.company.erp.system.application.dto.ApprovalRequestResponse;
import com.company.erp.system.domain.ApprovalAction;
import com.company.erp.system.domain.ApprovalActionType;
import com.company.erp.system.domain.ApprovalRequest;
import com.company.erp.system.domain.ApprovalRequestStatus;
import com.company.erp.system.infrastructure.persistence.ApprovalActionRepository;
import com.company.erp.system.infrastructure.persistence.ApprovalRequestRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Lịch sử duyệt của chứng từ lập trước D-61 (2026-10-21: hệ thống không còn bước duyệt). Chỉ đọc lịch sử và đóng yêu
 * cầu duyệt còn treo khi module sở hữu hủy chứng từ đang chờ duyệt cũ; không tạo yêu cầu duyệt mới.
 */
@Service
public class ApprovalService {

    private final ApprovalRequestRepository requestRepository;
    private final ApprovalActionRepository actionRepository;
    private final ApprovalResponseMapper mapper;
    private final Clock clock;

    public ApprovalService(ApprovalRequestRepository requestRepository, ApprovalActionRepository actionRepository,
                           ApprovalResponseMapper mapper, Clock clock) {
        this.requestRepository = requestRepository;
        this.actionRepository = actionRepository;
        this.mapper = mapper;
        this.clock = clock;
    }

    /** Hủy yêu cầu đang chờ khi chứng từ bị hủy. Không có yêu cầu đang chờ thì bỏ qua. */
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

    /** Khóa yêu cầu đang chờ theo khóa chính; người khác vừa xử lý xong thì không còn đang chờ. */
    private Optional<ApprovalRequest> lockPending(String documentType, Long documentId) {
        return requestRepository.findPendingId(documentType, documentId)
                .flatMap(requestRepository::findByIdForUpdate)
                .filter(request -> request.getStatus() == ApprovalRequestStatus.PENDING);
    }

    private static String normalize(String text) {
        return text == null || text.isBlank() ? null : text.trim();
    }
}
