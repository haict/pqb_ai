package com.company.erp.system.api;

import com.company.erp.common.api.ApiResponse;
import com.company.erp.system.application.CurrentUser;
import com.company.erp.system.application.ParameterService;
import com.company.erp.system.application.dto.ParameterResponse;
import com.company.erp.system.application.dto.UpdateParameterRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Tham số hệ thống")
@RestController
@RequestMapping("/api/v1/parameters")
public class ParameterController {

    private final ParameterService parameterService;

    public ParameterController(ParameterService parameterService) {
        this.parameterService = parameterService;
    }

    @Operation(summary = "Tham số của công ty", description = "Danh mục cố định theo migration, không phân trang.")
    @GetMapping
    @PreAuthorize("hasAuthority('parameter:view')")
    public ApiResponse<List<ParameterResponse>> list(@AuthenticationPrincipal CurrentUser user,
                                                     @RequestParam Long companyId) {
        return ApiResponse.ok(parameterService.list(user, companyId));
    }

    @Operation(summary = "Sửa giá trị tham số",
            description = "Cần phạm vi toàn công ty. Gửi version đã đọc (409 CONCURRENT_MODIFICATION khi đã thay đổi). "
                    + "422 PARAMETER_LOCKED khi tham số đã khóa vì đã phát sinh số liệu ghi sổ.")
    @PutMapping("/{key}")
    @PreAuthorize("hasAuthority('parameter:update')")
    public ApiResponse<ParameterResponse> update(@AuthenticationPrincipal CurrentUser user, @PathVariable String key,
                                                 @Valid @RequestBody UpdateParameterRequest request) {
        return ApiResponse.ok(parameterService.update(user, key, request));
    }
}
