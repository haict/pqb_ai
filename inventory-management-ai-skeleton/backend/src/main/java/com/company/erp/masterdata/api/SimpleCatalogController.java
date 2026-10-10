package com.company.erp.masterdata.api;

import com.company.erp.common.api.ApiResponse;
import com.company.erp.common.api.PageResponse;
import com.company.erp.common.application.IdempotentCommand;
import com.company.erp.common.domain.RecordStatus;
import com.company.erp.masterdata.application.SimpleCatalog;
import com.company.erp.masterdata.application.SimpleCatalogService;
import com.company.erp.masterdata.application.dto.CatalogItemRequest;
import com.company.erp.masterdata.application.dto.CatalogItemResponse;
import com.company.erp.masterdata.application.dto.UpdateCatalogItemRequest;
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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Danh mục mã - tên: {@code /units}, {@code /brands}, {@code /manufacturers}, {@code /partner-groups}. */
@Tag(name = "Danh mục mã - tên")
@RestController
@RequestMapping("/api/v1/{catalog:" + SimpleCatalog.PATH_PATTERN + "}")
public class SimpleCatalogController {

    private final SimpleCatalogService catalogService;

    public SimpleCatalogController(SimpleCatalogService catalogService) {
        this.catalogService = catalogService;
    }

    @Operation(summary = "Danh sách đơn vị tính / nhãn hiệu / nhà sản xuất / nhóm đối tác của công ty")
    @GetMapping
    @PreAuthorize("hasAuthority('catalog:view')")
    public ApiResponse<PageResponse<CatalogItemResponse>> search(@AuthenticationPrincipal CurrentUser user,
                                                                 @PathVariable String catalog,
                                                                 @RequestParam Long companyId,
                                                                 @RequestParam(required = false) String keyword,
                                                                 @RequestParam(required = false) RecordStatus status,
                                                                 Pageable pageable) {
        return ApiResponse.ok(PageResponse.from(catalogService.search(user, SimpleCatalog.fromPath(catalog),
                companyId, keyword, status, pageable), Function.identity()));
    }

    @Operation(summary = "Thêm mới", description = "countryCode chỉ dùng cho nhà sản xuất. Bắt buộc header Idempotency-Key.")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('catalog:manage')")
    public ApiResponse<CatalogItemResponse> create(@AuthenticationPrincipal CurrentUser user,
                                                   @PathVariable String catalog,
                                                   @RequestHeader(IdempotentCommand.HEADER) String idempotencyKey,
                                                   @Valid @RequestBody CatalogItemRequest request) {
        return ApiResponse.ok(catalogService.create(user, SimpleCatalog.fromPath(catalog), idempotencyKey, request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('catalog:manage')")
    public ApiResponse<CatalogItemResponse> update(@AuthenticationPrincipal CurrentUser user,
                                                   @PathVariable String catalog, @PathVariable Long id,
                                                   @Valid @RequestBody UpdateCatalogItemRequest request) {
        return ApiResponse.ok(catalogService.update(user, SimpleCatalog.fromPath(catalog), id, request));
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAuthority('catalog:manage')")
    public ApiResponse<CatalogItemResponse> changeStatus(@AuthenticationPrincipal CurrentUser user,
                                                         @PathVariable String catalog, @PathVariable Long id,
                                                         @Valid @RequestBody ChangeStatusRequest request) {
        return ApiResponse.ok(catalogService.changeStatus(user, SimpleCatalog.fromPath(catalog), id, request));
    }
}
