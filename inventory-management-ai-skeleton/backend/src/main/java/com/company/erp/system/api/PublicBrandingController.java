package com.company.erp.system.api;

import com.company.erp.common.api.ApiResponse;
import com.company.erp.system.application.BrandingLogo;
import com.company.erp.system.application.UiCustomizationService;
import com.company.erp.system.application.dto.BrandingResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.Duration;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Tên và logo theo khách hàng (D-72): không cần đăng nhập vì trang đăng nhập hiển thị; chỉ dữ liệu công khai. */
@Tag(name = "Tùy biến giao diện")
@RestController
@RequestMapping("/api/v1/public/branding")
public class PublicBrandingController {

    /** URL logo đổi theo logoVersion nên trình duyệt được giữ lâu. */
    private static final Duration LOGO_CACHE = Duration.ofDays(7);

    private final UiCustomizationService service;

    public PublicBrandingController(UiCustomizationService service) {
        this.service = service;
    }

    @Operation(summary = "Tên, phiên bản logo",
            description = "Không cần đăng nhập. appName / logoVersion = null: dùng mặc định của ứng dụng.")
    @GetMapping
    public ApiResponse<BrandingResponse> branding() {
        return ApiResponse.ok(service.branding());
    }

    @Operation(summary = "File logo", description = "Không cần đăng nhập. 404 NOT_FOUND khi chưa cấu hình hoặc file "
            + "không hợp lệ (không phải PNG / JPEG / WebP, quá dung lượng).")
    @GetMapping("/logo")
    public ResponseEntity<byte[]> logo() {
        BrandingLogo logo = service.requireLogo();
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(logo.contentType()))
                .cacheControl(CacheControl.maxAge(LOGO_CACHE).cachePublic())
                .eTag(logo.version())
                .body(logo.content());
    }
}
