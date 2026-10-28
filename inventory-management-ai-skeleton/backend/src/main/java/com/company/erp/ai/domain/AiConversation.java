package com.company.erp.ai.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * Hội thoại với trợ lý của một người dùng (docs/14 mục 5.4, 7). Không phải chứng từ: người dùng tự xóa, tác vụ nền xóa
 * khi quá {@code ai.chat.retention-days} kể từ tin nhắn cuối.
 */
@Entity
@Table(name = "ai_conversations")
public class AiConversation {

    /** Bằng độ dài cột {@code title}. */
    public static final int MAX_TITLE = 200;
    private static final String DEFAULT_TITLE = "Hội thoại mới";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "title", nullable = false, length = MAX_TITLE)
    private String title;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "last_message_at", nullable = false)
    private Instant lastMessageAt;

    protected AiConversation() {
    }

    public AiConversation(Long companyId, Long userId, String title, Instant now) {
        this.companyId = companyId;
        this.userId = userId;
        this.title = title == null || title.isBlank() ? DEFAULT_TITLE : shorten(title.strip());
        this.createdAt = now;
        this.lastMessageAt = now;
    }

    public boolean ownedBy(Long user) {
        return userId.equals(user);
    }

    /** Câu hỏi đầu tiên làm tiêu đề khi người dùng chưa đặt tên. */
    public void touch(String question, Instant now) {
        if (DEFAULT_TITLE.equals(title) && question != null && !question.isBlank()) {
            title = shorten(question.strip().replaceAll("\\s+", " "));
        }
        lastMessageAt = now;
    }

    private static String shorten(String value) {
        return value.length() <= MAX_TITLE ? value : value.substring(0, MAX_TITLE - 1) + "…";
    }

    public Long getId() {
        return id;
    }

    public Long getCompanyId() {
        return companyId;
    }

    public String getTitle() {
        return title;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getLastMessageAt() {
        return lastMessageAt;
    }
}
