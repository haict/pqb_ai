package com.company.erp.system.infrastructure.security;

import com.company.erp.common.domain.ErrorCode;
import com.company.erp.system.application.CurrentUser;
import com.company.erp.system.application.LicenseService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Chế độ chỉ đọc khi bản quyền không hiệu lực (D-59): chặn mọi request ghi của người đã đăng nhập, trả
 * 403 LICENSE_INACTIVE. Vẫn cho: xem (GET), đăng nhập / đổi mật khẩu, nạp license, ngừng hoạt động công ty (để gỡ
 * trạng thái công ty ngoài license). Không đăng ký thành bean để không chạy hai lần.
 */
class LicenseWriteGuardFilter extends OncePerRequestFilter {

    private static final Set<String> READ_METHODS = Set.of("GET", "HEAD", "OPTIONS");
    private static final Pattern COMPANY_STATUS = Pattern.compile("^/api/v1/companies/\\d+/status$");

    private final LicenseService licenseService;
    private final JsonSecurityErrorHandler errorHandler;

    LicenseWriteGuardFilter(LicenseService licenseService, JsonSecurityErrorHandler errorHandler) {
        this.licenseService = licenseService;
        this.errorHandler = errorHandler;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (isWrite(request) && authenticated() && !exempt(request) && !licenseService.writable()) {
            errorHandler.write(response, ErrorCode.LICENSE_INACTIVE);
            return;
        }
        chain.doFilter(request, response);
    }

    private static boolean isWrite(HttpServletRequest request) {
        return !READ_METHODS.contains(request.getMethod());
    }

    /** Request chưa đăng nhập để Spring Security trả 401 như bình thường. */
    private static boolean authenticated() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null && authentication.getPrincipal() instanceof CurrentUser;
    }

    private static boolean exempt(HttpServletRequest request) {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        return path.startsWith("/api/v1/auth/") || path.equals("/api/v1/license")
                || COMPANY_STATUS.matcher(path).matches();
    }
}
