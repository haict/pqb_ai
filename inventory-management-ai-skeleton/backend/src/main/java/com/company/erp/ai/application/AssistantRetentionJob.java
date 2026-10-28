package com.company.erp.ai.application;

import com.company.erp.ai.infrastructure.AiConversationRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Xóa hội thoại không có tin nhắn mới quá {@code ai.chat.retention-days} ngày (Q-05). Chạy lại nhiều lần không gây tác
 * dụng phụ.
 */
@Component
public class AssistantRetentionJob {

    private static final Logger log = LoggerFactory.getLogger(AssistantRetentionJob.class);

    private final AiConversationRepository conversations;
    private final AiProperties properties;
    private final Clock clock;

    public AssistantRetentionJob(AiConversationRepository conversations, AiProperties properties, Clock clock) {
        this.conversations = conversations;
        this.properties = properties;
        this.clock = clock;
    }

    @Scheduled(cron = "${ai.chat.retention-cron}")
    @Transactional
    public int deleteExpired() {
        Instant cutoff = Instant.now(clock).minus(Duration.ofDays(properties.chat().retentionDays()));
        int deleted = conversations.deleteInactiveBefore(cutoff);
        if (deleted > 0) {
            log.info("Đã xóa {} hội thoại trợ lý quá {} ngày", deleted, properties.chat().retentionDays());
        }
        return deleted;
    }
}
