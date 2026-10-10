package com.company.erp.system.application;

import com.company.erp.common.domain.BusinessException;
import com.company.erp.common.domain.ErrorCode;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Đếm lần đăng nhập sai theo IP trong cửa sổ trượt (security.md mục 7). Khóa theo tài khoản (BR-SYS-23) chặn dò
 * một tài khoản; bộ đếm này chặn dò rải trên nhiều tài khoản từ một IP. Lưu trong bộ nhớ: đủ cho một máy chủ (D-32),
 * khởi động lại thì đếm lại từ đầu.
 */
@Component
public class LoginRateLimiter {

    private final LoginRateLimitProperties properties;
    private final Clock clock;
    private final Map<String, Deque<Instant>> failures = new ConcurrentHashMap<>();

    public LoginRateLimiter(LoginRateLimitProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
    }

    /** Từ chối khi IP đã sai quá số lần cho phép trong cửa sổ hiện tại. */
    public void checkAllowed(String ipAddress) {
        if (ipAddress == null) {
            return;
        }
        Deque<Instant> attempts = failures.get(ipAddress);
        if (attempts == null) {
            return;
        }
        synchronized (attempts) {
            prune(attempts, Instant.now(clock));
            if (attempts.size() >= properties.maxFailures()) {
                throw new BusinessException(ErrorCode.TOO_MANY_REQUESTS,
                        "Đăng nhập sai quá nhiều lần từ địa chỉ này, vui lòng thử lại sau.");
            }
        }
    }

    public void recordFailure(String ipAddress) {
        if (ipAddress == null) {
            return;
        }
        Deque<Instant> attempts = failures.computeIfAbsent(ipAddress, ip -> new ArrayDeque<>());
        synchronized (attempts) {
            Instant now = Instant.now(clock);
            prune(attempts, now);
            attempts.addLast(now);
        }
    }

    /** Dọn IP không còn lần sai trong cửa sổ để bộ nhớ không tăng mãi. */
    @Scheduled(fixedDelayString = "PT5M")
    void evictExpired() {
        Instant now = Instant.now(clock);
        failures.entrySet().removeIf(entry -> {
            synchronized (entry.getValue()) {
                prune(entry.getValue(), now);
                return entry.getValue().isEmpty();
            }
        });
    }

    private void prune(Deque<Instant> attempts, Instant now) {
        Instant threshold = now.minus(properties.window());
        while (!attempts.isEmpty() && !attempts.peekFirst().isAfter(threshold)) {
            attempts.pollFirst();
        }
    }
}
