package com.company.erp.system.api;

import com.company.erp.common.api.ApiResponse;
import com.company.erp.system.application.AuthService;
import com.company.erp.system.application.ClientInfo;
import com.company.erp.system.application.CurrentUser;
import com.company.erp.system.application.SessionTokens;
import com.company.erp.system.application.dto.ChangePasswordRequest;
import com.company.erp.system.application.dto.LoginRequest;
import com.company.erp.system.application.dto.MeResponse;
import com.company.erp.system.application.dto.TokenResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.WebUtils;

@Tag(name = "Xác thực")
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private static final String TOKEN_TYPE = "Bearer";

    private final AuthService authService;
    private final RefreshCookies refreshCookies;
    private final Clock clock;

    public AuthController(AuthService authService, RefreshCookies refreshCookies, Clock clock) {
        this.authService = authService;
        this.refreshCookies = refreshCookies;
        this.clock = clock;
    }

    @Operation(summary = "Đăng nhập", description = "Trả access token; refresh token đặt trong cookie httpOnly. "
            + "Sai thông tin hoặc tài khoản bị khóa: 401 INVALID_CREDENTIALS.")
    @PostMapping("/login")
    public ResponseEntity<ApiResponse<TokenResponse>> login(@Valid @RequestBody LoginRequest request,
                                                            HttpServletRequest http) {
        return withSession(http, authService.login(request, clientOf(http)));
    }

    @Operation(summary = "Làm mới access token bằng refresh token trong cookie (xoay vòng token)",
            description = "Phiên hết hạn hoặc token bị dùng lại (thu hồi cả phiên): 401 SESSION_EXPIRED.")
    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<TokenResponse>> refresh(HttpServletRequest http) {
        return withSession(http, authService.refresh(readRefreshCookie(http), clientOf(http)));
    }

    @Operation(summary = "Đăng xuất: thu hồi phiên và xóa cookie")
    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout(HttpServletRequest http) {
        authService.logout(readRefreshCookie(http), clientOf(http));
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, refreshCookies.clear(http).toString())
                .body(ApiResponse.ok(null));
    }

    @Operation(summary = "Thông tin người dùng đăng nhập, quyền, chi nhánh được làm việc")
    @GetMapping("/me")
    public ApiResponse<MeResponse> me(@AuthenticationPrincipal CurrentUser user) {
        return ApiResponse.ok(authService.me(user.id()));
    }

    @Operation(summary = "Đổi mật khẩu của chính mình; mọi phiên khác bị thu hồi")
    @PostMapping("/change-password")
    public ResponseEntity<ApiResponse<TokenResponse>> changePassword(@AuthenticationPrincipal CurrentUser user,
                                                                     @Valid @RequestBody ChangePasswordRequest request,
                                                                     HttpServletRequest http) {
        return withSession(http, authService.changePassword(user, request, clientOf(http)));
    }

    private ResponseEntity<ApiResponse<TokenResponse>> withSession(HttpServletRequest http, SessionTokens tokens) {
        long expiresIn = Duration.between(Instant.now(clock), tokens.accessTokenExpiresAt()).toSeconds();
        TokenResponse body = new TokenResponse(tokens.accessToken(), TOKEN_TYPE, expiresIn,
                tokens.sessionExpiresAt(), authService.me(tokens.userId()));
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE,
                        refreshCookies.issue(http, tokens.refreshToken(), tokens.sessionExpiresAt()).toString())
                .body(ApiResponse.ok(body));
    }

    private String readRefreshCookie(HttpServletRequest http) {
        var cookie = WebUtils.getCookie(http, refreshCookies.name());
        return cookie == null ? null : cookie.getValue();
    }

    static ClientInfo clientOf(HttpServletRequest http) {
        return new ClientInfo(http.getRemoteAddr(), http.getHeader(HttpHeaders.USER_AGENT));
    }
}
