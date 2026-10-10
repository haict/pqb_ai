package com.company.erp.system.api;

import com.company.erp.common.api.ApiResponse;
import com.company.erp.common.api.PageResponse;
import com.company.erp.common.application.IdempotentCommand;
import com.company.erp.system.application.CurrentUser;
import com.company.erp.system.application.DocumentNumberFormatService;
import com.company.erp.system.application.dto.DocumentNumberFormatResponse;
import com.company.erp.system.application.dto.ScheduleDocumentNumberFormatRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.function.Function;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Cấu hình số chứng từ")
@RestController
@RequestMapping("/api/v1/document-number-formats")
public class DocumentNumberFormatController {

    private final DocumentNumberFormatService formatService;

    public DocumentNumberFormatController(DocumentNumberFormatService formatService) {
        this.formatService = formatService;
    }

    @Operation(summary = "Danh sách cấu hình số chứng từ (đang áp dụng, đặt trước, đã thay thế)")
    @GetMapping
    @PreAuthorize("hasAuthority('document-number:view')")
    public ApiResponse<PageResponse<DocumentNumberFormatResponse>> search(
            @AuthenticationPrincipal CurrentUser user,
            @RequestParam Long companyId,
            @RequestParam(required = false) String documentType,
            Pageable pageable) {
        return ApiResponse.ok(PageResponse.from(formatService.search(user, companyId, documentType, pageable),
                Function.identity()));
    }

    @Operation(summary = "Đặt cấu hình số chứng từ",
            description = "Loại chứng từ đã cấp số: hiệu lực từ chu kỳ sau (đầu tháng sau nếu đặt lại theo tháng, "
                    + "đầu năm sau nếu theo năm hoặc đổi chu kỳ). Chưa cấp số: hiệu lực ngay. "
                    + "Cần phạm vi toàn công ty. Bắt buộc header Idempotency-Key.")
    @PostMapping
    @PreAuthorize("hasAuthority('document-number:update')")
    public ApiResponse<DocumentNumberFormatResponse> schedule(
            @AuthenticationPrincipal CurrentUser user,
            @RequestHeader(IdempotentCommand.HEADER) String idempotencyKey,
            @Valid @RequestBody ScheduleDocumentNumberFormatRequest request) {
        return ApiResponse.ok(formatService.schedule(user, idempotencyKey, request));
    }

    @Operation(summary = "Hủy cấu hình đặt trước (chưa có hiệu lực)",
            description = "Cấu hình đã có hiệu lực: 409 CONFIGURATION_IN_EFFECT.")
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('document-number:update')")
    public ApiResponse<Void> deleteScheduled(@AuthenticationPrincipal CurrentUser user, @PathVariable Long id) {
        formatService.deleteScheduled(user, id);
        return ApiResponse.ok(null);
    }
}
