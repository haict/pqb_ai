package com.company.erp.system.application;

import com.company.erp.common.api.PageRequests;
import com.company.erp.common.domain.BusinessException;
import com.company.erp.system.application.dto.NotificationResponse;
import com.company.erp.system.domain.Notification;
import com.company.erp.system.infrastructure.persistence.NotificationRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.Collection;
import java.util.Set;
import java.util.TreeSet;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Thông báo trong ứng dụng (BR-SYS-25). Module khác gọi {@link #notify} trong transaction nghiệp vụ: thông báo
 * cùng commit hoặc rollback với dữ liệu. Email làm sau (cần máy chủ SMTP).
 */
@Service
public class NotificationService {

    private static final Set<String> SORTABLE = Set.of("createdAt");

    private final NotificationRepository repository;
    private final Clock clock;

    public NotificationService(NotificationRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void notify(Collection<Long> userIds, NotificationMessage message) {
        Instant now = Instant.now(clock);
        // Thứ tự cố định để các giao dịch chèn đồng thời không khóa chéo nhau.
        for (Long userId : new TreeSet<>(userIds)) {
            repository.insertIfAbsent(userId, message.eventKey(), message.category(), message.title(),
                    message.message(), message.linkType(), message.linkId(), now);
        }
    }

    @Transactional(readOnly = true)
    public Page<NotificationResponse> list(CurrentUser actor, boolean unreadOnly, Pageable pageable) {
        Pageable checked = PageRequests.checked(pageable, SORTABLE, Sort.by(Sort.Order.desc("createdAt")));
        Page<Notification> page = unreadOnly
                ? repository.findByUserIdAndReadAtIsNull(actor.id(), checked)
                : repository.findByUserId(actor.id(), checked);
        return page.map(NotificationService::toResponse);
    }

    @Transactional(readOnly = true)
    public long unreadCount(CurrentUser actor) {
        return repository.countByUserIdAndReadAtIsNull(actor.id());
    }

    /** Thông báo của người khác trả 404, không tiết lộ là có tồn tại. */
    @Transactional
    public NotificationResponse markRead(CurrentUser actor, Long id) {
        Notification notification = repository.findById(id)
                .filter(item -> item.getUserId().equals(actor.id()))
                .orElseThrow(BusinessException::notFound);
        notification.markRead(Instant.now(clock));
        return toResponse(notification);
    }

    @Transactional
    public int markAllRead(CurrentUser actor) {
        return repository.markAllRead(actor.id(), Instant.now(clock));
    }

    private static NotificationResponse toResponse(Notification notification) {
        return new NotificationResponse(notification.getId(), notification.getCategory(), notification.getTitle(),
                notification.getMessage(), notification.getLinkType(), notification.getLinkId(),
                notification.getCreatedAt(), notification.getReadAt());
    }
}
