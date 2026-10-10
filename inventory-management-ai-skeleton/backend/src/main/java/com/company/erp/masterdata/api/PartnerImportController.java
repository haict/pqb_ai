package com.company.erp.masterdata.api;

import com.company.erp.common.api.ApiResponse;
import com.company.erp.common.application.IdempotentCommand;
import com.company.erp.masterdata.application.MasterDataImportService;
import com.company.erp.masterdata.application.dto.ImportResultResponse;
import com.company.erp.system.application.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** Nhập đối tác từ Excel (BR-UX-05, BR-MIG-01). */
@Tag(name = "Đối tác")
@RestController
@RequestMapping("/api/v1/partners")
public class PartnerImportController {

    private final MasterDataImportService importService;

    public PartnerImportController(MasterDataImportService importService) {
        this.importService = importService;
    }

    @Operation(summary = "Tải file mẫu nhập đối tác (.xlsx)")
    @GetMapping("/import-template")
    @PreAuthorize("hasAuthority('partner:manage')")
    public ResponseEntity<byte[]> importTemplate() {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"mau-nhap-doi-tac.xlsx\"")
                .contentType(ProductController.XLSX)
                .body(importService.partnerTemplate());
    }

    @Operation(summary = "Nhập đối tác từ Excel",
            description = "Tất cả hoặc không: có lỗi thì trả 400 kèm lỗi theo dòng. Bắt buộc Idempotency-Key.")
    @PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('partner:manage')")
    public ApiResponse<ImportResultResponse> importPartners(@AuthenticationPrincipal CurrentUser user,
                                                            @RequestHeader(IdempotentCommand.HEADER) String idempotencyKey,
                                                            @RequestParam Long companyId,
                                                            @RequestPart("file") MultipartFile file) {
        return ApiResponse.ok(importService.importPartners(user, companyId, idempotencyKey, file));
    }
}
