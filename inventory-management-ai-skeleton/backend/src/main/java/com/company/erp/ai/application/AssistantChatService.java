package com.company.erp.ai.application;

import com.company.erp.ai.application.AiServiceGateway.ChatReply;
import com.company.erp.ai.application.AiServiceGateway.ChatTurn;
import com.company.erp.ai.application.AssistantExchangeStore.Registered;
import com.company.erp.ai.application.dto.AssistantExchangeResponse;
import com.company.erp.ai.application.dto.AssistantMessageRequest;
import com.company.erp.ai.domain.AiMessage;
import com.company.erp.ai.domain.AssistantMessageStatus;
import com.company.erp.common.domain.BusinessException;
import com.company.erp.common.domain.ErrorCode;
import com.company.erp.system.application.CurrentUser;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Một lượt hỏi trợ lý L1 (docs/14 mục 5.2): lưu câu hỏi, gọi AI Service (tìm đoạn tài liệu + LLM) ngoài transaction,
 * lưu câu trả lời. Trợ lý chỉ đọc tài liệu hướng dẫn, không có công cụ ghi (BR-AI-08).
 *
 * <p>Gửi lại cùng {@code Idempotency-Key} (AT-13): câu hỏi đã có trả lời thì trả lại, không gọi AI; đang xử lý thì
 * {@code REQUEST_IN_PROGRESS}; lần trước lỗi thì hỏi lại.
 */
@Service
public class AssistantChatService {

    private static final Logger log = LoggerFactory.getLogger(AssistantChatService.class);
    /** Thêm vào thời gian chờ AI để coi câu hỏi PENDING là treo (máy chủ dừng giữa chừng). */
    private static final Duration STALE_MARGIN = Duration.ofSeconds(30);

    private final AiProperties properties;
    private final AiAvailability availability;
    private final AiConcurrencyGuard guard;
    private final AiServiceGateway gateway;
    private final AssistantRateLimiter rateLimiter;
    private final AssistantExchangeStore store;
    private final Clock clock;

    public AssistantChatService(AiProperties properties, AiAvailability availability, AiConcurrencyGuard guard,
                                AiServiceGateway gateway, AssistantRateLimiter rateLimiter,
                                AssistantExchangeStore store, Clock clock) {
        this.properties = properties;
        this.availability = availability;
        this.guard = guard;
        this.gateway = gateway;
        this.rateLimiter = rateLimiter;
        this.store = store;
        this.clock = clock;
    }

    public AssistantExchangeResponse send(CurrentUser actor, Long conversationId, String idempotencyKey,
                                          AssistantMessageRequest request) {
        String content = request.content().strip();
        if (content.length() > properties.chat().maxMessageChars()) {
            throw new BusinessException(ErrorCode.ASSISTANT_MESSAGE_TOO_LONG, "Câu hỏi tối đa "
                    + properties.chat().maxMessageChars() + " ký tự.");
        }
        Registered registered = store.register(actor, conversationId, idempotencyKey, content);
        AiMessage question = store.question(registered.questionId());
        if (!registered.created()) {
            if (question.getStatus() == AssistantMessageStatus.ANSWERED) {
                return store.exchange(question);
            }
            if (question.inProgress(Instant.now(clock), properties.chat().timeout().plus(STALE_MARGIN))) {
                throw new BusinessException(ErrorCode.REQUEST_IN_PROGRESS);
            }
            store.retry(question.getId());
        }
        return ask(actor, question);
    }

    private AssistantExchangeResponse ask(CurrentUser actor, AiMessage question) {
        long start = clock.millis();
        String error = null;
        try {
            rateLimiter.acquire(actor.id());
            if (!availability.chatAvailable()) {
                throw new BusinessException(ErrorCode.AI_SERVICE_UNAVAILABLE);
            }
            List<ChatTurn> history = store.history(question, properties.chat().maxHistoryTurns(),
                    properties.chat().maxContextChars());
            ChatReply reply = guard.runChat(() -> gateway.chat(history, question.getContent()));
            return store.answer(question.getId(), reply, (int) (clock.millis() - start));
        } catch (BusinessException ex) {
            error = ex.errorCode().name();
            if (ex.errorCode() == ErrorCode.AI_SERVICE_UNAVAILABLE) {
                availability.markUnavailable();
            }
            store.fail(question.getId(), error);
            throw ex;
        } catch (RuntimeException ex) {
            error = ErrorCode.INTERNAL_ERROR.name();
            store.fail(question.getId(), error);
            throw ex;
        } finally {
            // Chỉ metadata (BR-AI-10): không ghi câu hỏi, câu trả lời.
            log.info("ASSISTANT conversationId={} messageId={} error={} durationMs={}", question.getConversationId(),
                    question.getId(), error == null ? "-" : error, clock.millis() - start);
        }
    }
}
