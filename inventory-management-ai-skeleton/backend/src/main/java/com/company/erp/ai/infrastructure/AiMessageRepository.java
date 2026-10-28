package com.company.erp.ai.infrastructure;

import com.company.erp.ai.domain.AiMessage;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AiMessageRepository extends JpaRepository<AiMessage, Long> {

    Page<AiMessage> findByConversationId(Long conversationId, Pageable pageable);

    /** Tin nhắn trước {@code beforeId}, mới nhất trước: lịch sử gửi kèm câu hỏi. */
    List<AiMessage> findByConversationIdAndIdLessThanOrderByIdDesc(Long conversationId, Long beforeId,
                                                                     Pageable pageable);

    Optional<AiMessage> findByReplyToId(Long questionId);
}
