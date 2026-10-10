package com.company.erp.masterdata.api;

import com.company.erp.common.api.ApiResponse;
import com.company.erp.common.api.PageResponse;
import com.company.erp.common.application.IdempotentCommand;
import com.company.erp.common.domain.RecordStatus;
import com.company.erp.masterdata.application.ComplianceService;
import com.company.erp.masterdata.application.dto.ComplianceDocumentRequest;
import com.company.erp.masterdata.application.dto.ComplianceDocumentResponse;
import com.company.erp.masterdata.application.dto.ComplianceDocumentTypeRequest;
import com.company.erp.masterdata.application.dto.ComplianceDocumentTypeResponse;
import com.company.erp.masterdata.application.dto.UpdateComplianceDocumentRequest;
import com.company.erp.masterdata.application.dto.UpdateComplianceDocumentTypeRequest;
import com.company.erp.masterdata.domain.ComplianceOwnerType;
import com.company.erp.system.application.CurrentUser;
import com.company.erp.system.application.dto.ChangeStatusRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.function.Function;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Loại hồ sơ / giấy phép và hồ sơ cụ thể có hạn (BR-MD-44, D-28). */
@Tag(name = "Hồ sơ, giấy phép")
@RestController
public class ComplianceController {

    private static final String TYPES = "/api/v1/compliance-document-types";
    private static final String DOCUMENTS = "/api/v1/compliance-documents";

    private final ComplianceService complianceService;

    public ComplianceController(ComplianceService complianceService) {
        this.complianceService = complianceService;
    }

    @GetMapping(TYPES)
    @PreAuthorize("hasAuthority('compliance:view')")
    public ApiResponse<PageResponse<ComplianceDocumentTypeResponse>> searchTypes(
            @AuthenticationPrincipal CurrentUser user, @RequestParam Long companyId,
            @RequestParam(required = false) RecordStatus status, Pageable pageable) {
        return ApiResponse.ok(PageResponse.from(complianceService.searchTypes(user, companyId, status, pageable),
                Function.identity()));
    }

    @Operation(summary = "Thêm loại hồ sơ", description = "Bắt buộc header Idempotency-Key.")
    @PostMapping(TYPES)
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('catalog:manage')")
    public ApiResponse<ComplianceDocumentTypeResponse> createType(
            @AuthenticationPrincipal CurrentUser user, @RequestHeader(IdempotentCommand.HEADER) String idempotencyKey,
            @Valid @RequestBody ComplianceDocumentTypeRequest request) {
        return ApiResponse.ok(complianceService.createType(user, idempotencyKey, request));
    }

    @PutMapping(TYPES + "/{id}")
    @PreAuthorize("hasAuthority('catalog:manage')")
    public ApiResponse<ComplianceDocumentTypeResponse> updateType(
            @AuthenticationPrincipal CurrentUser user, @PathVariable Long id,
            @Valid @RequestBody UpdateComplianceDocumentTypeRequest request) {
        return ApiResponse.ok(complianceService.updateType(user, id, request));
    }

    @PatchMapping(TYPES + "/{id}/status")
    @PreAuthorize("hasAuthority('catalog:manage')")
    public ApiResponse<ComplianceDocumentTypeResponse> changeTypeStatus(
            @AuthenticationPrincipal CurrentUser user, @PathVariable Long id,
            @Valid @RequestBody ChangeStatusRequest request) {
        return ApiResponse.ok(complianceService.changeTypeStatus(user, id, request));
    }

    @Operation(summary = "Danh sách hồ sơ",
            description = "expiringWithinDays: hồ sơ đang hiệu lực sẽ hết hạn trong số ngày tới (kể cả đã hết hạn).")
    @GetMapping(DOCUMENTS)
    @PreAuthorize("hasAuthority('compliance:view')")
    public ApiResponse<PageResponse<ComplianceDocumentResponse>> searchDocuments(
            @AuthenticationPrincipal CurrentUser user, @RequestParam Long companyId,
            @RequestParam(required = false) ComplianceOwnerType ownerType,
            @RequestParam(required = false) Long ownerId,
            @RequestParam(required = false) Long documentTypeId,
            @RequestParam(required = false) Integer expiringWithinDays,
            @RequestParam(required = false) RecordStatus status, Pageable pageable) {
        return ApiResponse.ok(PageResponse.from(complianceService.searchDocuments(user, companyId, ownerType, ownerId,
                documentTypeId, expiringWithinDays, status, pageable), Function.identity()));
    }

    @Operation(summary = "Thêm hồ sơ", description = "Bắt buộc header Idempotency-Key.")
    @PostMapping(DOCUMENTS)
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('compliance:manage')")
    public ApiResponse<ComplianceDocumentResponse> createDocument(
            @AuthenticationPrincipal CurrentUser user, @RequestHeader(IdempotentCommand.HEADER) String idempotencyKey,
            @Valid @RequestBody ComplianceDocumentRequest request) {
        return ApiResponse.ok(complianceService.createDocument(user, idempotencyKey, request));
    }

    @PutMapping(DOCUMENTS + "/{id}")
    @PreAuthorize("hasAuthority('compliance:manage')")
    public ApiResponse<ComplianceDocumentResponse> updateDocument(
            @AuthenticationPrincipal CurrentUser user, @PathVariable Long id,
            @Valid @RequestBody UpdateComplianceDocumentRequest request) {
        return ApiResponse.ok(complianceService.updateDocument(user, id, request));
    }

    @PatchMapping(DOCUMENTS + "/{id}/status")
    @PreAuthorize("hasAuthority('compliance:manage')")
    public ApiResponse<ComplianceDocumentResponse> changeDocumentStatus(
            @AuthenticationPrincipal CurrentUser user, @PathVariable Long id,
            @Valid @RequestBody ChangeStatusRequest request) {
        return ApiResponse.ok(complianceService.changeDocumentStatus(user, id, request));
    }
}
