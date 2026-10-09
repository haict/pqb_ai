package com.company.erp.system.infrastructure.security;

import com.company.erp.system.application.CurrentUser;
import java.util.Optional;
import org.springframework.data.domain.AuditorAware;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/** Người thực hiện cho cột {@code created_by/updated_by}; rỗng khi do hệ thống thực hiện. */
@Component("auditorAware")
class SecurityAuditorAware implements AuditorAware<Long> {

    @Override
    public Optional<Long> getCurrentAuditor() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof CurrentUser user) {
            return Optional.of(user.id());
        }
        return Optional.empty();
    }
}
