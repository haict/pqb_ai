package com.company.erp.masterdata.api;

import com.company.erp.common.api.ApiResponse;
import com.company.erp.common.api.PageResponse;
import com.company.erp.common.application.IdempotentCommand;
import com.company.erp.common.domain.RecordStatus;
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
import com.company.erp.masterdata.application.TaxRateService;
import com.company.erp.masterdata.application.dto.TaxRateRequest;
import com.company.erp.masterdata.application.dto.TaxRateResponse;
import com.company.erp.masterdata.application.dto.UpdateTaxRateRequest;

@Tag(name = "Thuế suất")
@RestController
@RequestMapping("/api/v1/tax-rates")
public class TaxRateController {

    private final TaxRateService taxRateService;

    public TaxRateController(TaxRateService taxRateService) {
        this.taxRateService = taxRateService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('catalog:view')")
    public ApiResponse<PageResponse<TaxRateResponse>> search(@AuthenticationPrincipal CurrentUser user,
            @RequestParam Long companyId,
            @RequestParam(required = false) RecordStatus status,
            Pageable pageable) {
        return ApiResponse.ok(PageResponse.from(taxRateService.search(user, companyId, status, pageable), Function.identity()));
    }

    @Operation(summary = "Thêm mới", description = "Bắt buộc header Idempotency-Key.")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('catalog:manage')")
    public ApiResponse<TaxRateResponse> create(@AuthenticationPrincipal CurrentUser user,
                                      @RequestHeader(IdempotentCommand.HEADER) String idempotencyKey,
                                      @Valid @RequestBody TaxRateRequest request) {
        return ApiResponse.ok(taxRateService.create(user, idempotencyKey, request));
    }

    @Operation(summary = "Sửa", description = "Gửi version đã đọc; dữ liệu đã đổi: 409 CONCURRENT_MODIFICATION.")
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('catalog:manage')")
    public ApiResponse<TaxRateResponse> update(@AuthenticationPrincipal CurrentUser user, @PathVariable Long id,
                                      @Valid @RequestBody UpdateTaxRateRequest request) {
        return ApiResponse.ok(taxRateService.update(user, id, request));
    }

    @Operation(summary = "Ngừng hoạt động / kích hoạt lại")
    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAuthority('catalog:manage')")
    public ApiResponse<TaxRateResponse> changeStatus(@AuthenticationPrincipal CurrentUser user, @PathVariable Long id,
                                            @Valid @RequestBody ChangeStatusRequest request) {
        return ApiResponse.ok(taxRateService.changeStatus(user, id, request));
    }
}
