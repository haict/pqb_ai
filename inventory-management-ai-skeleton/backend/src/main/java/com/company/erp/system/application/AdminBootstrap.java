package com.company.erp.system.application;

import com.company.erp.system.domain.Company;
import com.company.erp.system.domain.DataScopeType;
import com.company.erp.system.domain.Role;
import com.company.erp.system.domain.User;
import com.company.erp.system.infrastructure.persistence.CompanyRepository;
import com.company.erp.system.infrastructure.persistence.RoleRepository;
import com.company.erp.system.infrastructure.persistence.UserRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Tạo quản trị viên đầu tiên khi database chưa có người dùng (uat/prod không có dữ liệu mẫu).
 * Chỉ chạy khi đặt {@code ERP_BOOTSTRAP_ADMIN_PASSWORD}; quản trị viên phải đổi mật khẩu khi đăng nhập.
 */
@Component
class AdminBootstrap implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminBootstrap.class);
    private static final String ADMIN_ROLE = "SYSTEM_ADMIN";

    private final UserRepository userRepository;
    private final CompanyRepository companyRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final PasswordPolicy passwordPolicy;
    private final AuthProperties properties;
    private final Clock clock;

    AdminBootstrap(UserRepository userRepository, CompanyRepository companyRepository, RoleRepository roleRepository,
                   PasswordEncoder passwordEncoder, PasswordPolicy passwordPolicy, AuthProperties properties,
                   Clock clock) {
        this.userRepository = userRepository;
        this.companyRepository = companyRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
        this.passwordPolicy = passwordPolicy;
        this.properties = properties;
        this.clock = clock;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        AuthProperties.BootstrapAdmin admin = properties.bootstrapAdmin();
        if (admin == null || admin.password() == null || admin.password().isBlank() || userRepository.count() > 0) {
            return;
        }
        passwordPolicy.validate("ERP_BOOTSTRAP_ADMIN_PASSWORD", admin.password(), admin.username());
        Company company = companyRepository.findAll().stream().findFirst()
                .orElseGet(() -> companyRepository.save(new Company("MAIN", "Công ty (đổi tên sau khi cài đặt)",
                        null, null, null, null)));
        Role adminRole = roleRepository.findByCode(ADMIN_ROLE)
                .orElseThrow(() -> new IllegalStateException("Thiếu vai trò " + ADMIN_ROLE + " (migration V2)"));
        User user = new User(company.getId(), null, admin.username(), "Quản trị hệ thống", null, null,
                passwordEncoder.encode(admin.password()), false, Instant.now(clock));
        user.replaceRoles(List.of(adminRole));
        user.addDataScope(new User.ScopeAssignment(DataScopeType.COMPANY, company.getId()));
        userRepository.save(user);
        log.info("Đã tạo quản trị viên đầu tiên '{}'; bắt buộc đổi mật khẩu ở lần đăng nhập đầu", admin.username());
    }
}
