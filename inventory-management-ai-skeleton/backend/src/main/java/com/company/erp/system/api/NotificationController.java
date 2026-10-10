package com.company.erp.system.api;

import com.company.erp.common.api.ApiResponse;
import com.company.erp.common.api.PageResponse;
import com.company.erp.system.application.CurrentUser;
import com.company.erp.system.application.NotificationService;
import com.company.erp.system.application.dto.NotificationResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.Map;
import java.util.function.Function;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Thông báo của chính người đang đăng nhập (BR-SYS-25). */
@Tag(name = "Thông báo")
@RestController
@RequestMapping("/api/v1/notifications")
public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<PageResponse<NotificationResponse>> list(@AuthenticationPrincipal CurrentUser user,
                                                                @RequestParam(defaultValue = "false") boolean unreadOnly,
                                                                Pageable pageable) {
        return ApiResponse.ok(PageResponse.from(notificationService.list(user, unreadOnly, pageable),
                Function.identity()));
    }

    @Operation(summary = "Số thông báo chưa đọc")
    @GetMapping("/unread-count")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Map<String, Long>> unreadCount(@AuthenticationPrincipal CurrentUser user) {
        return ApiResponse.ok(Map.of("count", notificationService.unreadCount(user)));
    }

    @PostMapping("/{id}/read")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<NotificationResponse> markRead(@AuthenticationPrincipal CurrentUser user,
                                                      @PathVariable Long id) {
        return ApiResponse.ok(notificationService.markRead(user, id));
    }

    @PostMapping("/read-all")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Map<String, Integer>> markAllRead(@AuthenticationPrincipal CurrentUser user) {
        return ApiResponse.ok(Map.of("updated", notificationService.markAllRead(user)));
    }
}
