package com.company.erp.finance.api;

import com.company.erp.finance.application.CashCountPostingService;
import com.company.erp.finance.application.CashCountService;
import com.company.erp.finance.application.dto.CashCountRequest;
import com.company.erp.finance.application.dto.CashCountResponse;
import com.company.erp.common.api.ApiResponse;
import com.company.erp.common.api.PageResponse;
import com.company.erp.common.application.IdempotentCommand;
import com.company.erp.finance.application.dto.ReasonRequest;
import com.company.erp.finance.application.dto.ReverseRequest;
import com.company.erp.finance.domain.FinanceDocumentStatus;
import com.company.erp.system.application.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.LocalDate;
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

/** Kiểm kê quỹ tiền mặt (KKQ, BR-CSH-05; D-61 không qua duyệt): ghi sổ chênh lệch thừa / thiếu. */
@Tag(name = "Kiểm kê quỹ")
@RestController
@RequestMapping("/api/v1/cash-counts")
public class CashCountController {

    private final CashCountService service;
    private final CashCountPostingService postingService;

    public CashCountController(CashCountService service, CashCountPostingService postingService) {
        this.service = service;
        this.postingService = postingService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('cash-count:view')")
    public ApiResponse<PageResponse<CashCountResponse>> search(@AuthenticationPrincipal CurrentUser user,
            @RequestParam Long companyId, @RequestParam(required = false) Long branchId,
            @RequestParam(required = false) Long cashAccountId,
            @RequestParam(required = false) FinanceDocumentStatus status,
            @RequestParam(required = false) LocalDate dateFrom, @RequestParam(required = false) LocalDate dateTo,
            @RequestParam(required = false) String keyword, Pageable pageable) {
        return ApiResponse.ok(PageResponse.from(service.search(user, new CashCountService.CashCountFilter(companyId, branchId, cashAccountId, status,
                dateFrom, dateTo, keyword), pageable), Function.identity()));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('cash-count:view')")
    public ApiResponse<CashCountResponse> get(@AuthenticationPrincipal CurrentUser user, @PathVariable Long id) {
        return ApiResponse.ok(service.get(user, id));
    }

    @Operation(summary = "Lập chứng từ", description = "Bắt buộc Idempotency-Key.")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('cash-count:create')")
    public ApiResponse<CashCountResponse> create(@AuthenticationPrincipal CurrentUser user,
            @RequestHeader(IdempotentCommand.HEADER) String idempotencyKey, @Valid @RequestBody CashCountRequest request) {
        return ApiResponse.ok(service.create(user, idempotencyKey, request));
    }

    @Operation(summary = "Sửa (DRAFT)", description = "Gửi version; đã đổi: 409 CONCURRENT_MODIFICATION.")
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('cash-count:create')")
    public ApiResponse<CashCountResponse> update(@AuthenticationPrincipal CurrentUser user, @PathVariable Long id,
                                      @Valid @RequestBody CashCountRequest request) {
        return ApiResponse.ok(service.update(user, id, request));
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAuthority('cash-count:create')")
    public ApiResponse<CashCountResponse> cancel(@AuthenticationPrincipal CurrentUser user, @PathVariable Long id,
            @RequestHeader(IdempotentCommand.HEADER) String idempotencyKey, @Valid @RequestBody ReasonRequest request) {
        return ApiResponse.ok(service.cancel(user, id, idempotencyKey, request));
    }

    @Operation(summary = "Ghi sổ", description = "So số đếm với số dư sổ quỹ đến hết ngày kiểm kê; chênh lệch sinh sổ quỹ và bút toán.")
    @PostMapping("/{id}/post")
    @PreAuthorize("hasAuthority('cash-count:post')")
    public ApiResponse<CashCountResponse> post(@AuthenticationPrincipal CurrentUser user, @PathVariable Long id,
            @RequestHeader(IdempotentCommand.HEADER) String idempotencyKey) {
        return ApiResponse.ok(postingService.post(user, id, idempotencyKey));
    }

    @Operation(summary = "Đảo", description = "Bắt buộc lý do; quỹ không đủ để đảo chênh lệch thừa: 422 CASH_BALANCE_NEGATIVE.")
    @PostMapping("/{id}/reverse")
    @PreAuthorize("hasAuthority('cash-count:reverse')")
    public ApiResponse<CashCountResponse> reverse(@AuthenticationPrincipal CurrentUser user, @PathVariable Long id,
            @RequestHeader(IdempotentCommand.HEADER) String idempotencyKey, @Valid @RequestBody ReverseRequest request) {
        return ApiResponse.ok(postingService.reverse(user, id, idempotencyKey, request));
    }
}
