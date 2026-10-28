package com.company.erp.ai.application;

import com.company.erp.common.domain.BusinessException;
import com.company.erp.common.domain.ErrorCode;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * Giới hạn số câu hỏi của một người dùng trong 60 giây ({@code ai.rate-limit.per-user-per-minute}, docs/14 mục 8). Đếm
 * trong bộ nhớ của một tiến trình: đủ cho triển khai một máy chủ (1–5 người dùng đồng thời, docs/14 mục 9).
 */
@Component
public class AssistantRateLimiter {

    private static final Duration WINDOW = Duration.ofMinutes(1);

    private final int limit;
    private final Clock clock;
    private final Map<Long, Deque<Instant>> requests = new ConcurrentHashMap<>();

    public AssistantRateLimiter(AiProperties properties, Clock clock) {
        this.limit = properties.rateLimit().perUserPerMinute();
        this.clock = clock;
    }

    public void acquire(Long userId) {
        Instant now = Instant.now(clock);
        Deque<Instant> window = requests.computeIfAbsent(userId, id -> new ArrayDeque<>());
        synchronized (window) {
            while (!window.isEmpty() && !window.peekFirst().isAfter(now.minus(WINDOW))) {
                window.pollFirst();
            }
            if (window.size() >= limit) {
                throw new BusinessException(ErrorCode.AI_BUSY, "Bạn đã hỏi quá " + limit
                        + " câu trong một phút, vui lòng thử lại sau ít giây.");
            }
            window.addLast(now);
        }
    }
}
