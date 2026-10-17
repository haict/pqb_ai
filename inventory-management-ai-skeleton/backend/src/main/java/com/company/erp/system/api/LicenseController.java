package com.company.erp.system.api;

import com.company.erp.common.api.ApiResponse;
import com.company.erp.system.application.CurrentUser;
import com.company.erp.system.application.LicenseService;
import com.company.erp.system.application.dto.InstallLicenseRequest;
import com.company.erp.system.application.dto.LicenseStatusResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Bản quyền phần mềm (D-59): trạng thái cho mọi người đã đăng nhập (banner nhắc hạn), nạp license cho quản trị. */
@Tag(name = "Bản quyền phần mềm")
@RestController
@RequestMapping("/api/v1/license")
public class LicenseController {

    private final LicenseService licenseService;

    public LicenseController(LicenseService licenseService) {
        this.licenseService = licenseService;
    }

    @Operation(summary = "Trạng thái bản quyền", description = "writable = false: hệ thống đang ở chế độ chỉ đọc.")
    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<LicenseStatusResponse> status() {
        return ApiResponse.ok(licenseService.status());
    }

    @Operation(summary = "Nạp license", description = "Quyền license:manage. Thay license đang dùng bằng license mới "
            + "(nạp lại cùng nội dung không đổi kết quả). Sai chữ ký, hết hạn: 400 tại trường license; ghi audit.")
    @PutMapping
    @PreAuthorize("hasAuthority('license:manage')")
    public ApiResponse<LicenseStatusResponse> install(@AuthenticationPrincipal CurrentUser user,
                                                     @Valid @RequestBody InstallLicenseRequest request) {
        return ApiResponse.ok(licenseService.install(user, request.license()));
    }
}
