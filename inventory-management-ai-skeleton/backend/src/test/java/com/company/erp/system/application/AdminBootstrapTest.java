package com.company.erp.system.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.company.erp.common.domain.BusinessException;
import com.company.erp.system.domain.Company;
import com.company.erp.system.domain.DataScopeType;
import com.company.erp.system.domain.Role;
import com.company.erp.system.domain.User;
import com.company.erp.system.infrastructure.persistence.CompanyRepository;
import com.company.erp.system.infrastructure.persistence.RoleRepository;
import com.company.erp.system.infrastructure.persistence.UserRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;

class AdminBootstrapTest {

    private final UserRepository users = mock(UserRepository.class);
    private final CompanyRepository companies = mock(CompanyRepository.class);
    private final RoleRepository roles = mock(RoleRepository.class);
    private final PasswordEncoder encoder = PasswordEncoderFactories.createDelegatingPasswordEncoder();
    private final Clock clock = Clock.fixed(Instant.parse("2026-10-09T00:00:00Z"), ZoneOffset.UTC);

    private AdminBootstrap bootstrap(String password) {
        AuthProperties properties = new AuthProperties("unit-test-secret-0123456789-abcdefghijklmnop",
                Duration.ofMinutes(15), Duration.ofMinutes(30), Duration.ofHours(12), "ERP_REFRESH", true,
                new AuthProperties.Password(8, true, true), new AuthProperties.Lockout(5, Duration.ofMinutes(15)),
                new AuthProperties.BootstrapAdmin("admin", password));
        return new AdminBootstrap(users, companies, roles, encoder, new PasswordPolicy(properties), properties, clock);
    }

    @Test
    @DisplayName("DB chưa có người dùng: tạo admin có vai trò SYSTEM_ADMIN, phạm vi toàn công ty, bắt buộc đổi mật khẩu")
    void createsFirstAdmin() throws Exception {
        Company company = mock(Company.class);
        when(company.getId()).thenReturn(1L);
        when(users.count()).thenReturn(0L);
        when(companies.findAll()).thenReturn(List.of(company));
        when(roles.findByCode("SYSTEM_ADMIN")).thenReturn(Optional.of(mock(Role.class)));

        bootstrap("Khoi@2026").run(null);

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(users).save(saved.capture());
        User admin = saved.getValue();
        assertThat(admin.getUsername()).isEqualTo("admin");
        assertThat(admin.isMustChangePassword()).isTrue();
        assertThat(encoder.matches("Khoi@2026", admin.getPasswordHash())).isTrue();
        assertThat(admin.getRoles()).hasSize(1);
        assertThat(admin.getDataScopes()).singleElement()
                .satisfies(scope -> assertThat(scope.getScopeType()).isEqualTo(DataScopeType.COMPANY));
    }

    @Test
    @DisplayName("Đã có người dùng hoặc không đặt mật khẩu: không làm gì")
    void skipsWhenUsersExistOrNoPassword() throws Exception {
        when(users.count()).thenReturn(3L);
        bootstrap("Khoi@2026").run(null);
        bootstrap("").run(null);

        verify(users, never()).save(any());
    }

    @Test
    @DisplayName("Mật khẩu khởi tạo yếu: dừng khởi động thay vì tạo admin yếu")
    void rejectsWeakBootstrapPassword() {
        when(users.count()).thenReturn(0L);

        assertThatThrownBy(() -> bootstrap("12345678").run(null)).isInstanceOf(BusinessException.class);
        verify(users, never()).save(any());
    }
}
