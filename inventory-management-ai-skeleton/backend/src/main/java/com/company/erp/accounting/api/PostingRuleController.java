package com.company.erp.accounting.api;

import com.company.erp.accounting.application.PostingRuleService;
import com.company.erp.accounting.application.dto.PostingRuleRequest;
import com.company.erp.accounting.application.dto.PostingRuleResponse;
import com.company.erp.accounting.application.dto.UpdatePostingRuleRequest;
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

/** Quy tắc hạch toán cấu hình (BR-ACC-02, ADR-11). */
@Tag(name = "Quy tắc hạch toán")
@RestController
@RequestMapping("/api/v1/posting-rules")
public class PostingRuleController {

    private final PostingRuleService service;

    public PostingRuleController(PostingRuleService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('posting-rule:view')")
    public ApiResponse<PageResponse<PostingRuleResponse>> search(@AuthenticationPrincipal CurrentUser user,
            @RequestParam Long companyId, @RequestParam(required = false) String documentType,
            @RequestParam(required = false) String transactionCode,
            @RequestParam(required = false) RecordStatus status, Pageable pageable) {
        return ApiResponse.ok(PageResponse.from(service.search(user, companyId, documentType, transactionCode, status,
                pageable), Function.identity()));
    }

    @Operation(summary = "Thêm quy tắc", description = "Bắt buộc header Idempotency-Key. Trùng điều kiện và ngày "
            + "bắt đầu: 409 DUPLICATE_CODE.")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('posting-rule:manage')")
    public ApiResponse<PostingRuleResponse> create(@AuthenticationPrincipal CurrentUser user,
                                                   @RequestHeader(IdempotentCommand.HEADER) String idempotencyKey,
                                                   @Valid @RequestBody PostingRuleRequest request) {
        return ApiResponse.ok(service.create(user, idempotencyKey, request));
    }

    @Operation(summary = "Sửa tài khoản, ngày hết hiệu lực, diễn giải",
            description = "Điều kiện và ngày bắt đầu không đổi. 409 CONCURRENT_MODIFICATION.")
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('posting-rule:manage')")
    public ApiResponse<PostingRuleResponse> update(@AuthenticationPrincipal CurrentUser user, @PathVariable Long id,
                                                   @Valid @RequestBody UpdatePostingRuleRequest request) {
        return ApiResponse.ok(service.update(user, id, request));
    }

    @Operation(summary = "Ngừng hoạt động / kích hoạt lại")
    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAuthority('posting-rule:manage')")
    public ApiResponse<PostingRuleResponse> changeStatus(@AuthenticationPrincipal CurrentUser user,
                                                         @PathVariable Long id,
                                                         @Valid @RequestBody ChangeStatusRequest request) {
        return ApiResponse.ok(service.changeStatus(user, id, request));
    }
}
