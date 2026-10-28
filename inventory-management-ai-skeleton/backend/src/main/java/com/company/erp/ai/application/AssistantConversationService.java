package com.company.erp.ai.application;

import com.company.erp.ai.application.dto.AssistantMessageResponse;
import com.company.erp.ai.application.dto.ConversationRequest;
import com.company.erp.ai.application.dto.ConversationResponse;
import com.company.erp.ai.domain.AiConversation;
import com.company.erp.ai.domain.AiMessage;
import com.company.erp.ai.infrastructure.AiConversationRepository;
import com.company.erp.ai.infrastructure.AiMessageRepository;
import com.company.erp.common.api.PageRequests;
import com.company.erp.common.api.PageResponse;
import com.company.erp.common.application.IdempotencyService;
import com.company.erp.common.application.IdempotentCommand;
import com.company.erp.common.domain.BusinessException;
import com.company.erp.system.application.CurrentUser;
import java.time.Clock;
import java.time.Instant;
import java.util.Set;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Hội thoại với trợ lý của chính người dùng (docs/14 mục 5.4): chỉ chủ sở hữu đọc / ghi / xóa; hội thoại của người khác
 * trả 404 như không tồn tại (chống IDOR).
 */
@Service
public class AssistantConversationService {

    private static final Set<String> CONVERSATION_SORT = Set.of("lastMessageAt", "createdAt", "title");
    private static final Set<String> MESSAGE_SORT = Set.of("id", "createdAt");

    private final AiConversationRepository conversations;
    private final AiMessageRepository messages;
    private final IdempotencyService idempotency;
    private final Clock clock;

    public AssistantConversationService(AiConversationRepository conversations, AiMessageRepository messages,
                                        IdempotencyService idempotency, Clock clock) {
        this.conversations = conversations;
        this.messages = messages;
        this.idempotency = idempotency;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public PageResponse<ConversationResponse> list(CurrentUser actor, Pageable pageable) {
        Pageable page = PageRequests.checked(pageable, CONVERSATION_SORT, Sort.by(Sort.Direction.DESC, "lastMessageAt"));
        return PageResponse.from(conversations.findByUserId(actor.id(), page),
                AssistantConversationService::toResponse);
    }

    @Transactional
    public ConversationResponse create(CurrentUser actor, String idempotencyKey, ConversationRequest request) {
        IdempotentCommand command = new IdempotentCommand(actor.id(), idempotencyKey,
                "POST /api/v1/assistant/conversations", request);
        return idempotency.execute(command, ConversationResponse.class, () -> toResponse(conversations.save(
                new AiConversation(actor.companyId(), actor.id(), request.title(), Instant.now(clock)))));
    }

    /** Tin nhắn theo thứ tự gửi (mặc định id tăng dần). */
    @Transactional(readOnly = true)
    public PageResponse<AssistantMessageResponse> messages(CurrentUser actor, Long conversationId,
                                                           Pageable pageable) {
        AiConversation conversation = owned(actor, conversationId);
        Pageable page = PageRequests.checked(pageable, MESSAGE_SORT, Sort.by("id"));
        return PageResponse.from(messages.findByConversationId(conversation.getId(), page),
                AssistantConversationService::toResponse);
    }

    /** Xóa hẳn hội thoại và tin nhắn (không phải chứng từ nghiệp vụ, docs/14 mục 7). */
    @Transactional
    public void delete(CurrentUser actor, Long conversationId) {
        conversations.delete(owned(actor, conversationId));
    }

    AiConversation owned(CurrentUser actor, Long conversationId) {
        return conversations.findById(conversationId)
                .filter(conversation -> conversation.ownedBy(actor.id()))
                .orElseThrow(BusinessException::notFound);
    }

    static ConversationResponse toResponse(AiConversation conversation) {
        return new ConversationResponse(conversation.getId(), conversation.getTitle(), conversation.getCreatedAt(),
                conversation.getLastMessageAt());
    }

    static AssistantMessageResponse toResponse(AiMessage message) {
        return new AssistantMessageResponse(message.getId(), message.getRole(), message.getContent(),
                message.getStatus(), message.citationList(), message.getGrounded(), message.getCreatedAt());
    }
}
