package com.company.erp.ai.infrastructure;

import com.company.erp.ai.domain.AiConversation;
import java.time.Instant;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AiConversationRepository extends JpaRepository<AiConversation, Long> {

    Page<AiConversation> findByUserId(Long userId, Pageable pageable);

    /** Tin nhắn bị xóa theo khóa ngoại ON DELETE CASCADE. */
    @Modifying
    @Query("delete from AiConversation c where c.lastMessageAt < :cutoff")
    int deleteInactiveBefore(@Param("cutoff") Instant cutoff);
}
