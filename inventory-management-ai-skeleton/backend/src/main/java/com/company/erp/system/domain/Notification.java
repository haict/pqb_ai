package com.company.erp.system.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/** Thông báo trong ứng dụng cho một người dùng (BR-SYS-25). Tạo bằng câu lệnh chống trùng theo {@code eventKey}. */
@Entity
@Table(name = "notifications")
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private Long userId;

    @Column(name = "event_key", nullable = false, length = 150, updatable = false)
    private String eventKey;

    @Column(name = "category", nullable = false, length = 30, updatable = false)
    private String category;

    @Column(name = "title", nullable = false, length = 200, updatable = false)
    private String title;

    @Column(name = "message", length = 1000, updatable = false)
    private String message;

    @Column(name = "link_type", length = 30, updatable = false)
    private String linkType;

    @Column(name = "link_id", updatable = false)
    private Long linkId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "read_at")
    private Instant readAt;

    protected Notification() {
    }

    public void markRead(Instant now) {
        if (readAt == null) {
            readAt = now;
        }
    }

    public Long getId() {
        return id;
    }

    public Long getUserId() {
        return userId;
    }

    public String getEventKey() {
        return eventKey;
    }

    public String getCategory() {
        return category;
    }

    public String getTitle() {
        return title;
    }

    public String getMessage() {
        return message;
    }

    public String getLinkType() {
        return linkType;
    }

    public Long getLinkId() {
        return linkId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getReadAt() {
        return readAt;
    }
}
