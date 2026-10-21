package com.company.erp.system.api;

import com.company.erp.common.api.ApiResponse;
import com.company.erp.system.application.DocumentTypeService;
import com.company.erp.system.application.dto.DocumentTypeResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
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

}
