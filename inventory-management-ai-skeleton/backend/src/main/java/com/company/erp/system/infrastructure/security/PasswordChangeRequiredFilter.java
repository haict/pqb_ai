package com.company.erp.system.infrastructure.security;

import com.company.erp.common.domain.ErrorCode;
import com.company.erp.system.application.CurrentUser;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Người dùng phải đổi mật khẩu (tài khoản mới, vừa được đặt lại mật khẩu) chỉ được gọi API xác thực;
 * mọi API khác trả 403 PASSWORD_CHANGE_REQUIRED. Không đăng ký thành bean để không chạy hai lần.
 */
class PasswordChangeRequiredFilter extends OncePerRequestFilter {

    private final JsonSecurityErrorHandler errorHandler;

    PasswordChangeRequiredFilter(JsonSecurityErrorHandler errorHandler) {
        this.errorHandler = errorHandler;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof CurrentUser user
                && user.mustChangePassword() && !isAuthEndpoint(request)) {
            errorHandler.write(response, ErrorCode.PASSWORD_CHANGE_REQUIRED);
            return;
        }
        chain.doFilter(request, response);
    }

    private static boolean isAuthEndpoint(HttpServletRequest request) {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        return path.startsWith("/api/v1/auth/");
    }
}
