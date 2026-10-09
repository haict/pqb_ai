package com.company.erp.system.api;

import com.company.erp.common.api.ApiResponse;
import com.company.erp.common.api.PageResponse;
import com.company.erp.system.application.CurrentUser;
import com.company.erp.system.application.LoginLogQueryService;
import com.company.erp.system.application.dto.LoginLogResponse;
import com.company.erp.system.domain.LoginLog.LoginEvent;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.Instant;
import java.util.function.Function;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Nhật ký đăng nhập")
@RestController
@RequestMapping("/api/v1/login-logs")
public class LoginLogController {

    private final LoginLogQueryService loginLogQueryService;

    public LoginLogController(LoginLogQueryService loginLogQueryService) {
        this.loginLogQueryService = loginLogQueryService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('login-log:view')")
    public ApiResponse<PageResponse<LoginLogResponse>> search(@AuthenticationPrincipal CurrentUser user,
                                                              @RequestParam(required = false) String username,
                                                              @RequestParam(required = false) LoginEvent event,
                                                              @RequestParam(required = false) Instant from,
                                                              @RequestParam(required = false) Instant to,
                                                              Pageable pageable) {
        return ApiResponse.ok(PageResponse.from(
                loginLogQueryService.search(user, username, event, from, to, pageable), Function.identity()));
    }
}
