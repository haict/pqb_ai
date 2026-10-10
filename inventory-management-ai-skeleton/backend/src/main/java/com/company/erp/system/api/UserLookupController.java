package com.company.erp.system.api;

import com.company.erp.common.api.ApiResponse;
import com.company.erp.system.application.CurrentUser;
import com.company.erp.system.application.UserLookupService;
import com.company.erp.system.application.dto.UserLookupResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Người dùng")
@RestController
public class UserLookupController {

    private final UserLookupService lookupService;

    public UserLookupController(UserLookupService lookupService) {
        this.lookupService = lookupService;
    }

    @Operation(summary = "Tra cứu người dùng đang hoạt động cho ô chọn",
            description = "Tối đa 50 kết quả, trong các công ty thuộc phạm vi dữ liệu. Chỉ trả ID, tên đăng nhập, họ tên.")
    @GetMapping("/api/v1/users/lookup")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<List<UserLookupResponse>> lookup(@AuthenticationPrincipal CurrentUser user,
                                                        @RequestParam(required = false) String keyword) {
        return ApiResponse.ok(lookupService.lookup(user, keyword));
    }
}
