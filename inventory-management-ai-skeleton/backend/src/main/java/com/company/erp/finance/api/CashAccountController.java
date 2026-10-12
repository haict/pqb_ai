package com.company.erp.finance.api;

import com.company.erp.common.api.ApiResponse;
import com.company.erp.common.api.PageResponse;
import com.company.erp.common.application.IdempotentCommand;
import com.company.erp.common.domain.RecordStatus;
import com.company.erp.finance.application.CashAccountService;
import com.company.erp.finance.application.dto.CashAccountRequest;
import com.company.erp.finance.application.dto.CashAccountResponse;
import com.company.erp.finance.application.dto.UpdateCashAccountRequest;
import com.company.erp.finance.domain.CashAccountType;
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

/** Quỹ tiền mặt, tài khoản ngân hàng (BR-CSH-01, BR-MD-46). Số dư chỉ đổi qua ghi sổ chứng từ thu chi. */
@Tag(name = "Quỹ, tài khoản ngân hàng")
@RestController
@RequestMapping("/api/v1/cash-accounts")
public class CashAccountController {

    private final CashAccountService service;

    public CashAccountController(CashAccountService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('cash-account:view')")
    public ApiResponse<PageResponse<CashAccountResponse>> search(@AuthenticationPrincipal CurrentUser user,
            @RequestParam Long companyId, @RequestParam(required = false) Long branchId,
            @RequestParam(required = false) CashAccountType accountType,
            @RequestParam(required = false) RecordStatus status,
            @RequestParam(required = false) String keyword, Pageable pageable) {
        CashAccountService.CashAccountFilter filter = new CashAccountService.CashAccountFilter(companyId, branchId,
                accountType, status, keyword);
        return ApiResponse.ok(PageResponse.from(service.search(user, filter, pageable), Function.identity()));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('cash-account:view')")
    public ApiResponse<CashAccountResponse> get(@AuthenticationPrincipal CurrentUser user, @PathVariable Long id) {
        return ApiResponse.ok(service.get(user, id));
    }

    @Operation(summary = "Thêm quỹ / tài khoản ngân hàng", description = "Bắt buộc Idempotency-Key. Mã trùng: 409 "
            + "DUPLICATE_CODE. Tài khoản kế toán phải là tài khoản chi tiết đang hoạt động.")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('cash-account:manage')")
    public ApiResponse<CashAccountResponse> create(@AuthenticationPrincipal CurrentUser user,
                                                   @RequestHeader(IdempotentCommand.HEADER) String idempotencyKey,
                                                   @Valid @RequestBody CashAccountRequest request) {
        return ApiResponse.ok(service.create(user, idempotencyKey, request));
    }

    @Operation(summary = "Sửa", description = "Gửi version; đã đổi: 409 CONCURRENT_MODIFICATION.")
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('cash-account:manage')")
    public ApiResponse<CashAccountResponse> update(@AuthenticationPrincipal CurrentUser user, @PathVariable Long id,
                                                   @Valid @RequestBody UpdateCashAccountRequest request) {
        return ApiResponse.ok(service.update(user, id, request));
    }

    @Operation(summary = "Ngừng / kích hoạt", description = "Quỹ còn số dư không ngừng được: 409 DATA_IN_USE.")
    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAuthority('cash-account:manage')")
    public ApiResponse<CashAccountResponse> changeStatus(@AuthenticationPrincipal CurrentUser user,
                                                         @PathVariable Long id,
                                                         @Valid @RequestBody ChangeStatusRequest request) {
        return ApiResponse.ok(service.changeStatus(user, id, request));
    }
}
