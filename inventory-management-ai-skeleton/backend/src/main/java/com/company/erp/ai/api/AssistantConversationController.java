package com.company.erp.ai.api;

import com.company.erp.ai.application.AssistantChatService;
import com.company.erp.ai.application.AssistantConversationService;
import com.company.erp.ai.application.dto.AssistantExchangeResponse;
import com.company.erp.ai.application.dto.AssistantMessageRequest;
import com.company.erp.ai.application.dto.AssistantMessageResponse;
import com.company.erp.ai.application.dto.ConversationRequest;
import com.company.erp.ai.application.dto.ConversationResponse;
import com.company.erp.common.api.ApiResponse;
import com.company.erp.common.api.PageResponse;
import com.company.erp.common.application.IdempotentCommand;
import com.company.erp.system.application.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Trợ lý AI hỏi đáp cách dùng ERP (D-64, AI-3). Chỉ đọc tài liệu hướng dẫn; không lưu chứng từ, không xem dữ liệu kinh
 * doanh. Hội thoại chỉ chủ sở hữu truy cập: của người khác trả 404.
 */
@Tag(name = "Trợ lý AI")
@RestController
@RequestMapping("/api/v1/assistant/conversations")
@PreAuthorize("hasAuthority('assistant:use')")
public class AssistantConversationController {

    private final AssistantConversationService conversations;
    private final AssistantChatService chat;

    public AssistantConversationController(AssistantConversationService conversations, AssistantChatService chat) {
        this.conversations = conversations;
        this.chat = chat;
    }

    @Operation(summary = "Hội thoại của tôi", description = "Sắp xếp: lastMessageAt (mặc định giảm dần), createdAt, title.")
    @GetMapping
    public ApiResponse<PageResponse<ConversationResponse>> list(@AuthenticationPrincipal CurrentUser user,
                                                                Pageable pageable) {
        return ApiResponse.ok(conversations.list(user, pageable));
    }

    @Operation(summary = "Tạo hội thoại", description = "Bắt buộc header Idempotency-Key. Tiêu đề trống thì lấy câu hỏi "
            + "đầu tiên.")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<ConversationResponse> create(@AuthenticationPrincipal CurrentUser user,
                                                    @RequestHeader(IdempotentCommand.HEADER) String idempotencyKey,
                                                    @Valid @RequestBody ConversationRequest request) {
        return ApiResponse.ok(conversations.create(user, idempotencyKey, request));
    }

    @Operation(summary = "Tin nhắn của hội thoại", description = "Mặc định theo thứ tự gửi (id tăng dần).")
    @GetMapping("/{id}/messages")
    public ApiResponse<PageResponse<AssistantMessageResponse>> messages(@AuthenticationPrincipal CurrentUser user,
                                                                        @PathVariable Long id, Pageable pageable) {
        return ApiResponse.ok(conversations.messages(user, id, pageable));
    }

    @Operation(summary = "Gửi câu hỏi, nhận câu trả lời",
            description = "Bắt buộc header Idempotency-Key: gửi lại cùng key không tạo câu hỏi trùng, không gọi AI lại "
                    + "khi đã có câu trả lời. Lỗi: 400 ASSISTANT_MESSAGE_TOO_LONG, 404 hội thoại không phải của mình, "
                    + "409 REQUEST_IN_PROGRESS, 429 AI_BUSY, 503 AI_SERVICE_UNAVAILABLE, 504 AI_TIMEOUT.")
    @PostMapping("/{id}/messages")
    public ApiResponse<AssistantExchangeResponse> send(@AuthenticationPrincipal CurrentUser user,
                                                       @PathVariable Long id,
                                                       @RequestHeader(IdempotentCommand.HEADER) String idempotencyKey,
                                                       @Valid @RequestBody AssistantMessageRequest request) {
        return ApiResponse.ok(chat.send(user, id, idempotencyKey, request));
    }

    @Operation(summary = "Xóa hội thoại của tôi", description = "Xóa hẳn hội thoại và tin nhắn (không phải chứng từ).")
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@AuthenticationPrincipal CurrentUser user, @PathVariable Long id) {
        conversations.delete(user, id);
        return ApiResponse.ok(null);
    }
}
