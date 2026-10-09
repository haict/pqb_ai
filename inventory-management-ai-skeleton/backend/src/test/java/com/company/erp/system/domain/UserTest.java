package com.company.erp.system.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class UserTest {

    private static final Instant NOW = Instant.parse("2026-10-09T03:00:00.123456Z");
    private static final Duration LOCK = Duration.ofMinutes(15);

    private User newUser() {
        return new User(1L, 1L, "nv01", "Nhân viên 01", null, null, "{bcrypt}x", false, NOW);
    }

    @Test
    @DisplayName("BR-SYS-23: chưa đủ số lần cho phép thì chỉ tăng bộ đếm, không khóa")
    void countsFailuresBelowLimit() {
        User.FailedLoginState state = newUser().afterFailedLogin(NOW, 5, LOCK);

        assertThat(state.lockedNow()).isFalse();
        assertThat(state.failedLoginCount()).isEqualTo(1);
        assertThat(state.lockedUntil()).isNull();
    }

    @Test
    @DisplayName("BR-SYS-23: lần sai chạm ngưỡng thì khóa tạm đúng thời hạn và xóa bộ đếm")
    void locksWhenReachingLimit() {
        User.FailedLoginState state = newUser().afterFailedLogin(NOW, 1, LOCK);

        assertThat(state.lockedNow()).isTrue();
        assertThat(state.failedLoginCount()).isZero();
        assertThat(state.lockedUntil()).isEqualTo(NOW.plus(LOCK));
    }

    @Test
    @DisplayName("Người dùng mới phải đổi mật khẩu; đổi mật khẩu thay phiên bản thông tin đăng nhập")
    void passwordChangeBumpsCredentialsVersion() {
        User user = newUser();
        assertThat(user.isMustChangePassword()).isTrue();
        long before = user.credentialsVersion();

        user.changePassword("{bcrypt}y", NOW.plusMillis(5), false);

        assertThat(user.isMustChangePassword()).isFalse();
        assertThat(user.credentialsVersion()).isEqualTo(before + 5);
    }
}
