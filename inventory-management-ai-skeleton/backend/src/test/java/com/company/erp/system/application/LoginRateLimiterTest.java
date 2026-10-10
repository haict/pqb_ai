package com.company.erp.system.application;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.company.erp.common.domain.BusinessException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class LoginRateLimiterTest {

    private Instant now = Instant.parse("2026-10-10T00:00:00Z");
    private final Clock clock = new Clock() {
        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    };
    private final LoginRateLimiter limiter =
            new LoginRateLimiter(new LoginRateLimitProperties(3, Duration.ofMinutes(15)), clock);

    @Test
    @DisplayName("security.md mục 7: IP sai quá số lần trong cửa sổ bị chặn (429), IP khác không ảnh hưởng")
    void blocksIpAfterTooManyFailures() {
        for (int i = 0; i < 3; i++) {
            limiter.checkAllowed("10.0.0.1");
            limiter.recordFailure("10.0.0.1");
        }

        assertThatThrownBy(() -> limiter.checkAllowed("10.0.0.1"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("quá nhiều lần");
        assertThatCode(() -> limiter.checkAllowed("10.0.0.2")).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Lần sai cũ hơn cửa sổ không còn được tính")
    void failuresExpireAfterWindow() {
        for (int i = 0; i < 3; i++) {
            limiter.recordFailure("10.0.0.1");
        }
        now = now.plus(Duration.ofMinutes(16));

        assertThatCode(() -> limiter.checkAllowed("10.0.0.1")).doesNotThrowAnyException();
    }
}
