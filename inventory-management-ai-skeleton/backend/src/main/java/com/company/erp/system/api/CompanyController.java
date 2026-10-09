package com.company.erp.system.api;

import com.company.erp.common.api.ApiResponse;
import com.company.erp.common.api.PageResponse;
import com.company.erp.common.application.IdempotentCommand;
import com.company.erp.common.domain.RecordStatus;
import com.company.erp.system.application.CompanyService;
import com.company.erp.system.application.CurrentUser;
import com.company.erp.system.application.dto.ChangeStatusRequest;
import com.company.erp.system.application.dto.CompanyResponse;
import com.company.erp.system.application.dto.CreateCompanyRequest;
import com.company.erp.system.application.dto.UpdateCompanyRequest;
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

@Tag(name = "Công ty")
@RestController
@RequestMapping("/api/v1/companies")
public class CompanyController {

    private final CompanyService companyService;

    public CompanyController(CompanyService companyService) {
        this.companyService = companyService;
    }

    @Operation(summary = "Danh sách công ty trong phạm vi dữ liệu")
    @GetMapping
    @PreAuthorize("hasAuthority('company:view')")
    public ApiResponse<PageResponse<CompanyResponse>> search(@AuthenticationPrincipal CurrentUser user,
                                                             @RequestParam(required = false) String keyword,
                                                             @RequestParam(required = false) RecordStatus status,
                                                             Pageable pageable) {
        return ApiResponse.ok(PageResponse.from(companyService.search(user, keyword, status, pageable),
                Function.identity()));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('company:view')")
    public ApiResponse<CompanyResponse> get(@AuthenticationPrincipal CurrentUser user, @PathVariable Long id) {
        return ApiResponse.ok(companyService.get(user, id));
    }

    @Operation(summary = "Tạo công ty", description = "Bắt buộc header Idempotency-Key.")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('company:create')")
    public ApiResponse<CompanyResponse> create(@AuthenticationPrincipal CurrentUser user,
                                               @RequestHeader(IdempotentCommand.HEADER) String idempotencyKey,
                                               @Valid @RequestBody CreateCompanyRequest request) {
        return ApiResponse.ok(companyService.create(user, idempotencyKey, request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('company:update')")
    public ApiResponse<CompanyResponse> update(@AuthenticationPrincipal CurrentUser user, @PathVariable Long id,
                                               @Valid @RequestBody UpdateCompanyRequest request) {
        return ApiResponse.ok(companyService.update(user, id, request));
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAuthority('company:update')")
    public ApiResponse<CompanyResponse> changeStatus(@AuthenticationPrincipal CurrentUser user, @PathVariable Long id,
                                                     @Valid @RequestBody ChangeStatusRequest request) {
        return ApiResponse.ok(companyService.changeStatus(user, id, request));
    }
}
