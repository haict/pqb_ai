package com.company.erp.system.api;

import com.company.erp.common.api.ApiResponse;
import com.company.erp.system.application.PostingModeService;
import com.company.erp.system.application.UiCustomizationService;
import com.company.erp.system.application.dto.PostingModeResponse;
import com.company.erp.system.application.dto.UiFeatureResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Cấu hình theo khách hàng: ẩn / đổi tên menu, nút (D-72), chế độ hạch toán (D-73); chỉ đọc, sửa bằng SQL tại
 * khách.
 */
@Tag(name = "Tùy biến giao diện")
@RestController
@RequestMapping("/api/v1")
public class UiCustomizationController {

    private final UiCustomizationService service;
    private final PostingModeService postingMode;

    public UiCustomizationController(UiCustomizationService service, PostingModeService postingMode) {
        this.service = service;
        this.postingMode = postingMode;
    }

    @Operation(summary = "Cấu hình menu, nút",
            description = "Mọi người đã đăng nhập. Khóa không có trong danh sách: bật, tên mặc định. Ẩn chỉ phục vụ "
                    + "giao diện; API vẫn kiểm tra quyền như cũ.")
    @GetMapping("/ui-features")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<List<UiFeatureResponse>> features() {
        return ApiResponse.ok(service.features());
    }

    @Operation(summary = "Chế độ hạch toán",
            description = "Mọi người đã đăng nhập. FULL: kho + công nợ + bút toán; INVENTORY_ONLY: chỉ kho (D-73).")
    @GetMapping("/posting-mode")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<PostingModeResponse> postingMode() {
        return ApiResponse.ok(new PostingModeResponse(postingMode.mode()));
    }
}
