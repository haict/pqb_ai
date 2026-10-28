package com.company.erp.ai.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;

/**
 * Một tin nhắn trong hội thoại (docs/14 mục 7). Câu trả lời lưu tên mục tài liệu được trích; không lưu đoạn tài liệu
 * hay prompt gửi LLM.
 */
@Entity
@Table(name = "ai_messages")
public class AiMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "conversation_id", nullable = false)
    private Long conversationId;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 20)
    private AssistantMessageRole role;

    @Column(name = "content", nullable = false, columnDefinition = "TEXT")
    private String content;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private AssistantMessageStatus status;

    @Column(name = "reply_to_id")
    private Long replyToId;

    @Column(name = "citations", columnDefinition = "TEXT")
    private String citations;

    @Column(name = "grounded")
    private Boolean grounded;

    @Column(name = "error_code", length = 50)
    private String errorCode;

    @Column(name = "duration_ms")
    private Integer durationMs;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected AiMessage() {
    }

    public static AiMessage question(Long conversationId, String content, Instant now) {
        AiMessage message = new AiMessage();
        message.conversationId = conversationId;
        message.role = AssistantMessageRole.USER;
        message.content = content;
        message.status = AssistantMessageStatus.PENDING;
        message.createdAt = now;
        return message;
    }

    public static AiMessage answer(AiMessage question, String content, List<String> citations, boolean grounded,
                                   int durationMs, Instant now) {
        AiMessage message = new AiMessage();
        message.conversationId = question.conversationId;
        message.role = AssistantMessageRole.ASSISTANT;
        message.content = content;
        message.status = AssistantMessageStatus.COMPLETED;
        message.replyToId = question.id;
        message.citations = citations.isEmpty() ? null : String.join("\n", citations);
        message.grounded = grounded;
        message.durationMs = durationMs;
        message.createdAt = now;
        return message;
    }

    public void markAnswered() {
        status = AssistantMessageStatus.ANSWERED;
        errorCode = null;
    }

    public void markFailed(String code) {
        status = AssistantMessageStatus.FAILED;
        errorCode = code;
    }

    /**
     * Câu hỏi đang được xử lý. Chờ quá {@code staleAfter} (máy chủ dừng giữa chừng) thì không còn tính là đang xử lý,
     * cho phép gửi lại.
     */
    public boolean inProgress(Instant now, Duration staleAfter) {
        return status == AssistantMessageStatus.PENDING && createdAt.plus(staleAfter).isAfter(now);
    }

    public void retry(Instant now) {
        status = AssistantMessageStatus.PENDING;
        errorCode = null;
        createdAt = now;
    }

    public List<String> citationList() {
        return citations == null ? List.of() : Arrays.asList(citations.split("\n"));
    }

    public Long getId() {
        return id;
    }

    public Long getConversationId() {
        return conversationId;
    }

    public AssistantMessageRole getRole() {
        return role;
    }

    public String getContent() {
        return content;
    }

    public AssistantMessageStatus getStatus() {
        return status;
    }

    public Long getReplyToId() {
        return replyToId;
    }

    public Boolean getGrounded() {
        return grounded;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
