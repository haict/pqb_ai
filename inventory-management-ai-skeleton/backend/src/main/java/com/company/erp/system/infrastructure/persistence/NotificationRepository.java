package com.company.erp.system.infrastructure.persistence;

import com.company.erp.system.domain.Notification;
import java.time.Instant;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    Page<Notification> findByUserId(Long userId, Pageable pageable);

    Page<Notification> findByUserIdAndReadAtIsNull(Long userId, Pageable pageable);

    long countByUserIdAndReadAtIsNull(Long userId);

    /** Tạo thông báo; cùng (người nhận, event_key) đã có thì bỏ qua (BR-GEN-54). */
    @Modifying
    @Query(nativeQuery = true, value = """
            insert ignore into notifications
                (user_id, event_key, category, title, message, link_type, link_id, created_at)
            values (:userId, :eventKey, :category, :title, :message, :linkType, :linkId, :now)
            """)
    int insertIfAbsent(@Param("userId") Long userId, @Param("eventKey") String eventKey,
                       @Param("category") String category, @Param("title") String title,
                       @Param("message") String message, @Param("linkType") String linkType,
                       @Param("linkId") Long linkId, @Param("now") Instant now);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update Notification n set n.readAt = :now where n.userId = :userId and n.readAt is null")
    int markAllRead(@Param("userId") Long userId, @Param("now") Instant now);
}
