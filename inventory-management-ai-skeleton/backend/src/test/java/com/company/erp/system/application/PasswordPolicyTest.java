package com.company.erp.system.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.company.erp.common.domain.BusinessException;
import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PasswordPolicyTest {

    private final PasswordPolicy policy = new PasswordPolicy(new AuthProperties(
            "unit-test-secret-0123456789-abcdefghijklmnop", Duration.ofMinutes(15), Duration.ofMinutes(30),
            Duration.ofHours(12), "ERP_REFRESH", true,
            new AuthProperties.Password(8, true, true),
            new AuthProperties.Lockout(5, Duration.ofMinutes(15)),
            new AuthProperties.BootstrapAdmin("admin", null)));

    @Test
    @DisplayName("Mật khẩu đủ 8 ký tự, có chữ và số thì hợp lệ")
    void acceptsStrongPassword() {
        assertThat(policy.problemsOf("Kho2026x", "nv01")).isEmpty();
    }

    @Test
    @DisplayName("Báo đủ mọi vi phạm: quá ngắn, thiếu số, trùng tên đăng nhập")
    void listsAllProblems() {
        assertThat(policy.problemsOf("abc", "nv01")).hasSize(2);
        assertThat(policy.problemsOf("nv01nv01", "NV01NV01")).containsExactly("không được trùng tên đăng nhập");
    }

    @Test
    @DisplayName("Vi phạm trả VALIDATION_ERROR gắn với trường")
    void throwsFieldError() {
        assertThatThrownBy(() -> policy.validate("newPassword", "12345678", "nv01"))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).violations())
                        .extracting("field").containsExactly("newPassword"));
    }

    @Test
    @DisplayName("Secret JWT thiếu hoặc ngắn hơn 32 byte làm ứng dụng không khởi động")
    void rejectsWeakJwtSecret() {
        assertThatThrownBy(() -> new AuthProperties("short", null, null, null, null, true, null, null, null))
                .isInstanceOf(IllegalStateException.class);
    }
}
