package com.company.erp.sales.api;

import com.company.erp.common.api.ApiResponse;
import com.company.erp.common.api.PageResponse;
import com.company.erp.common.application.IdempotentCommand;
import com.company.erp.sales.application.SalesComplaintService;
import com.company.erp.sales.application.dto.InspectComplaintRequest;
import com.company.erp.sales.application.dto.ReasonRequest;
import com.company.erp.sales.application.dto.ResolveComplaintRequest;
import com.company.erp.sales.application.dto.SalesComplaintRequest;
import com.company.erp.sales.application.dto.SalesComplaintResponse;
import com.company.erp.sales.domain.ComplaintStatus;
import com.company.erp.system.application.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.function.Function;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Tiếp nhận hàng lỗi khách báo (BR-SAL-34): tiếp nhận, kiểm tra, quyết định xử lý, hủy. */
@Tag(name = "Hàng lỗi khách báo")
@RestController
@RequestMapping("/api/v1/sales-complaints")
public class SalesComplaintController {

    private final SalesComplaintService service;

    public SalesComplaintController(SalesComplaintService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('sales-complaint:view')")
    public ApiResponse<PageResponse<SalesComplaintResponse>> search(@AuthenticationPrincipal CurrentUser user,
            @RequestParam Long companyId, @RequestParam(required = false) Long branchId,
            @RequestParam(required = false) ComplaintStatus status, @RequestParam(required = false) Long customerId,
            @RequestParam(required = false) String keyword, Pageable pageable) {
        return ApiResponse.ok(PageResponse.from(service.search(user, new SalesComplaintService.ComplaintFilter(
                companyId, branchId, status, customerId, keyword), pageable), Function.identity()));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('sales-complaint:view')")
    public ApiResponse<SalesComplaintResponse> get(@AuthenticationPrincipal CurrentUser user, @PathVariable Long id) {
        return ApiResponse.ok(service.get(user, id));
    }

    @Operation(summary = "Tiếp nhận", description = "Bắt buộc Idempotency-Key.")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('sales-complaint:create')")
    public ApiResponse<SalesComplaintResponse> create(@AuthenticationPrincipal CurrentUser user,
            @RequestHeader(IdempotentCommand.HEADER) String idempotencyKey,
            @Valid @RequestBody SalesComplaintRequest request) {
        return ApiResponse.ok(service.create(user, idempotencyKey, request));
    }

    @Operation(summary = "Sửa (chưa kiểm tra)", description = "Gửi version; đã đổi: 409 CONCURRENT_MODIFICATION.")
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('sales-complaint:create')")
    public ApiResponse<SalesComplaintResponse> update(@AuthenticationPrincipal CurrentUser user,
            @PathVariable Long id, @Valid @RequestBody SalesComplaintRequest request) {
        return ApiResponse.ok(service.update(user, id, request));
    }

    @PostMapping("/{id}/inspect")
    @PreAuthorize("hasAuthority('sales-complaint:resolve')")
    public ApiResponse<SalesComplaintResponse> inspect(@AuthenticationPrincipal CurrentUser user,
            @PathVariable Long id, @RequestHeader(IdempotentCommand.HEADER) String idempotencyKey,
            @Valid @RequestBody InspectComplaintRequest request) {
        return ApiResponse.ok(service.inspect(user, id, idempotencyKey, request));
    }

    @Operation(summary = "Quyết định xử lý", description = "Đổi / sửa / trả / bảo hành / từ chối; đổi, trả kèm chứng từ "
            + "trả hàng của cùng khách.")
    @PostMapping("/{id}/resolve")
    @PreAuthorize("hasAuthority('sales-complaint:resolve')")
    public ApiResponse<SalesComplaintResponse> resolve(@AuthenticationPrincipal CurrentUser user,
            @PathVariable Long id, @RequestHeader(IdempotentCommand.HEADER) String idempotencyKey,
            @Valid @RequestBody ResolveComplaintRequest request) {
        return ApiResponse.ok(service.resolve(user, id, idempotencyKey, request));
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAuthority('sales-complaint:create')")
    public ApiResponse<SalesComplaintResponse> cancel(@AuthenticationPrincipal CurrentUser user,
            @PathVariable Long id, @RequestHeader(IdempotentCommand.HEADER) String idempotencyKey,
            @Valid @RequestBody ReasonRequest request) {
        return ApiResponse.ok(service.cancel(user, id, idempotencyKey, request));
    }
}
