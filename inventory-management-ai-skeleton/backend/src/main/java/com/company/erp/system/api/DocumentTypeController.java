package com.company.erp.system.api;

import com.company.erp.common.api.ApiResponse;
import com.company.erp.system.application.CurrentUser;
import com.company.erp.system.application.DocumentTypeService;
import com.company.erp.system.application.dto.DocumentTypeResponse;
import com.company.erp.system.application.dto.UpdateDocumentTypeRequest;
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
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Loại chứng từ")
@RestController
@RequestMapping("/api/v1/document-types")
public class DocumentTypeController {

    private final DocumentTypeService documentTypeService;

    public DocumentTypeController(DocumentTypeService documentTypeService) {
        this.documentTypeService = documentTypeService;
    }

    @Operation(summary = "Danh mục loại chứng từ", description = "Dùng cho ô chọn; mọi người dùng đã đăng nhập.")
    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<List<DocumentTypeResponse>> list() {
        return ApiResponse.ok(documentTypeService.list());
    }

    @Operation(summary = "Đổi chế độ duyệt, cho phép tự duyệt của loại chứng từ")
    @PutMapping("/{code}")
    @PreAuthorize("hasAuthority('approval-workflow:manage')")
    public ApiResponse<DocumentTypeResponse> update(@AuthenticationPrincipal CurrentUser user,
                                                    @PathVariable String code,
                                                    @Valid @RequestBody UpdateDocumentTypeRequest request) {
        return ApiResponse.ok(documentTypeService.update(user, code, request));
    }
}
