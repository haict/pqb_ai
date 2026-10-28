package com.company.erp.ai.application;

import com.company.erp.ai.application.AiServiceGateway.ChatReply;
import com.company.erp.ai.application.AiServiceGateway.ChatTurn;
import com.company.erp.ai.application.dto.AssistantExchangeResponse;
import com.company.erp.ai.domain.AiConversation;
import com.company.erp.ai.domain.AiMessage;
import com.company.erp.ai.domain.AssistantMessageRole;
import com.company.erp.ai.domain.AssistantMessageStatus;
import com.company.erp.ai.infrastructure.AiConversationRepository;
import com.company.erp.ai.infrastructure.AiMessageRepository;
import com.company.erp.common.application.IdempotencyService;
import com.company.erp.common.application.IdempotentCommand;
import com.company.erp.common.domain.BusinessException;
import com.company.erp.system.application.CurrentUser;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Các bước có transaction của một lượt hỏi trợ lý. Mỗi bước là transaction ngắn riêng; không giữ transaction trong lúc
 * chờ AI Service (docs/14 mục 9.2).
 */
@Service
class AssistantExchangeStore {

    /** Câu hỏi đã lưu; {@code created} false khi gửi lại cùng {@code Idempotency-Key} (AT-13). */
    record Registered(Long questionId, boolean created) {
    }

    /** Kết quả lưu của lần đầu, chỉ để {@link IdempotencyService} trả lại cho lần gửi lại. */
    record QuestionRef(Long questionId) {
    }

    private final AssistantConversationService conversationService;
    private final AiConversationRepository conversations;
    private final AiMessageRepository messages;
    private final IdempotencyService idempotency;
    private final Clock clock;

    AssistantExchangeStore(AssistantConversationService conversationService, AiConversationRepository conversations,
                           AiMessageRepository messages, IdempotencyService idempotency, Clock clock) {
        this.conversationService = conversationService;
        this.conversations = conversations;
        this.messages = messages;
        this.idempotency = idempotency;
        this.clock = clock;
    }

    @Transactional
    public Registered register(CurrentUser actor, Long conversationId, String idempotencyKey, String content) {
        AiConversation conversation = conversationService.owned(actor, conversationId);
        IdempotentCommand command = new IdempotentCommand(actor.id(), idempotencyKey,
                "POST /api/v1/assistant/conversations/" + conversationId + "/messages", content);
        AtomicBoolean created = new AtomicBoolean(false);
        QuestionRef ref = idempotency.execute(command, QuestionRef.class, () -> {
            created.set(true);
            return new QuestionRef(messages.save(AiMessage.question(conversation.getId(), content,
                    Instant.now(clock))).getId());
        });
        return new Registered(ref.questionId(), created.get());
    }

    @Transactional(readOnly = true)
    public AiMessage question(Long questionId) {
        return messages.findById(questionId).orElseThrow(BusinessException::notFound);
    }

    @Transactional(readOnly = true)
    public AssistantExchangeResponse exchange(AiMessage question) {
        AiMessage answer = messages.findByReplyToId(question.getId()).orElseThrow(BusinessException::notFound);
        return new AssistantExchangeResponse(AssistantConversationService.toResponse(question),
                AssistantConversationService.toResponse(answer));
    }

    /** Gửi lại câu hỏi lỗi (hoặc treo do máy chủ dừng giữa chừng). */
    @Transactional
    public void retry(Long questionId) {
        messages.findById(questionId).orElseThrow(BusinessException::notFound).retry(Instant.now(clock));
    }

    /**
     * Ngữ cảnh gửi kèm: tối đa {@code maxTurns} tin nhắn trước câu hỏi (bỏ câu hỏi lỗi / đang chờ), tổng không quá
     * {@code maxChars} ký tự; thứ tự cũ trước.
     */
    @Transactional(readOnly = true)
    public List<ChatTurn> history(AiMessage question, int maxTurns, int maxChars) {
        List<ChatTurn> turns = new ArrayList<>();
        int used = 0;
        for (AiMessage message : messages.findByConversationIdAndIdLessThanOrderByIdDesc(question.getConversationId(),
                question.getId(), PageRequest.of(0, maxTurns))) {
            boolean usable = message.getRole() == AssistantMessageRole.ASSISTANT
                    || message.getStatus() == AssistantMessageStatus.ANSWERED;
            if (!usable) {
                continue;
            }
            used += message.getContent().length();
            if (used > maxChars) {
                break;
            }
            turns.add(new ChatTurn(message.getRole().name(), message.getContent()));
        }
        Collections.reverse(turns);
        return turns;
    }

    @Transactional
    public AssistantExchangeResponse answer(Long questionId, ChatReply reply, int durationMs) {
        AiMessage question = messages.findById(questionId).orElseThrow(BusinessException::notFound);
        Instant now = Instant.now(clock);
        question.markAnswered();
        AiMessage answer = messages.save(AiMessage.answer(question, reply.answer(), reply.citations(),
                reply.grounded(), durationMs, now));
        conversations.findById(question.getConversationId())
                .ifPresent(conversation -> conversation.touch(question.getContent(), now));
        return new AssistantExchangeResponse(AssistantConversationService.toResponse(question),
                AssistantConversationService.toResponse(answer));
    }

    @Transactional
    public void fail(Long questionId, String errorCode) {
        messages.findById(questionId).ifPresent(question -> question.markFailed(errorCode));
    }
}
