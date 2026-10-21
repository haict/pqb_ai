package com.company.erp.purchasing.api;

import com.company.erp.common.api.ApiResponse;
import com.company.erp.common.api.PageResponse;
import com.company.erp.common.application.IdempotentCommand;
import com.company.erp.purchasing.application.PurchaseCostAllocationPostingService;
import com.company.erp.purchasing.application.PurchaseCostAllocationService;
import com.company.erp.purchasing.application.dto.CostAllocationSourceResponse;
import com.company.erp.purchasing.application.dto.PurchaseCostAllocationRequest;
import com.company.erp.purchasing.application.dto.PurchaseCostAllocationResponse;
import com.company.erp.purchasing.application.dto.ReasonRequest;
import com.company.erp.purchasing.application.dto.ReverseRequest;
import com.company.erp.purchasing.domain.PurchaseDocumentStatus;
import com.company.erp.system.application.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
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

/**
 * Phân bổ chi phí mua hàng (PBC, BR-PUR-12, 16): dòng dịch vụ của hóa đơn mua phân bổ vào dòng phiếu nhận theo giá trị
 * hoặc số lượng; ghi sổ điều chỉnh giá trị nhập và bút toán; đảo.
 */
@Tag(name = "Phân bổ chi phí mua hàng")
@RestController
@RequestMapping("/api/v1/purchase-cost-allocations")
public class PurchaseCostAllocationController {

    private final PurchaseCostAllocationService service;
    private final PurchaseCostAllocationPostingService postingService;

    public PurchaseCostAllocationController(PurchaseCostAllocationService service, PurchaseCostAllocationPostingService postingService) {
        this.service = service;
        this.postingService = postingService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('purchase-cost-allocation:view')")
    public ApiResponse<PageResponse<PurchaseCostAllocationResponse>> search(
            @AuthenticationPrincipal CurrentUser user, @RequestParam Long companyId,
            @RequestParam(required = false) Long branchId,
            @RequestParam(required = false) PurchaseDocumentStatus status,
            @RequestParam(required = false) LocalDate dateFrom, @RequestParam(required = false) LocalDate dateTo,
            @RequestParam(required = false) String keyword, Pageable pageable) {
        return ApiResponse.ok(PageResponse.from(service.search(user,
                new PurchaseCostAllocationService.AllocationFilter(companyId, branchId, status, dateFrom, dateTo,
                        keyword), pageable), Function.identity()));
    }

    @Operation(summary = "Dòng chi phí còn phân bổ được", description = "Dòng dịch vụ của hóa đơn mua đã ghi sổ.")
    @GetMapping("/cost-lines")
    @PreAuthorize("hasAuthority('purchase-cost-allocation:view')")
    public ApiResponse<List<CostAllocationSourceResponse>> costLines(@AuthenticationPrincipal CurrentUser user,
            @RequestParam Long branchId) {
        return ApiResponse.ok(service.costLines(user, branchId));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('purchase-cost-allocation:view')")
    public ApiResponse<PurchaseCostAllocationResponse> get(@AuthenticationPrincipal CurrentUser user, @PathVariable Long id) {
        return ApiResponse.ok(service.get(user, id));
    }

    @Operation(summary = "Lập", description = "Bắt buộc Idempotency-Key. Vượt phần chi phí còn lại: 422 SOURCE_QUANTITY_EXCEEDED.")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('purchase-cost-allocation:create')")
    public ApiResponse<PurchaseCostAllocationResponse> create(@AuthenticationPrincipal CurrentUser user,
            @RequestHeader(IdempotentCommand.HEADER) String idempotencyKey,
            @Valid @RequestBody PurchaseCostAllocationRequest request) {
        return ApiResponse.ok(service.create(user, idempotencyKey, request));
    }

    @Operation(summary = "Sửa (DRAFT)", description = "Gửi version; đã đổi: 409 CONCURRENT_MODIFICATION.")
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('purchase-cost-allocation:create')")
    public ApiResponse<PurchaseCostAllocationResponse> update(@AuthenticationPrincipal CurrentUser user,
            @PathVariable Long id, @Valid @RequestBody PurchaseCostAllocationRequest request) {
        return ApiResponse.ok(service.update(user, id, request));
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAuthority('purchase-cost-allocation:create')")
    public ApiResponse<PurchaseCostAllocationResponse> cancel(@AuthenticationPrincipal CurrentUser user,
            @PathVariable Long id, @RequestHeader(IdempotentCommand.HEADER) String idempotencyKey,
            @Valid @RequestBody ReasonRequest request) {
        return ApiResponse.ok(service.cancel(user, id, idempotencyKey, request));
    }

    @Operation(summary = "Ghi sổ", description = "Điều chỉnh giá trị nhập theo dòng phiếu nhận (LANDED_COST), bút toán theo quy tắc PBC.")
    @PostMapping("/{id}/post")
    @PreAuthorize("hasAuthority('purchase-cost-allocation:post')")
    public ApiResponse<PurchaseCostAllocationResponse> post(@AuthenticationPrincipal CurrentUser user, @PathVariable Long id,
            @RequestHeader(IdempotentCommand.HEADER) String idempotencyKey) {
        return ApiResponse.ok(postingService.post(user, id, idempotencyKey));
    }

    @Operation(summary = "Đảo", description = "Đảo điều chỉnh giá trị nhập và bút toán.")
    @PostMapping("/{id}/reverse")
    @PreAuthorize("hasAuthority('purchase-cost-allocation:reverse')")
    public ApiResponse<PurchaseCostAllocationResponse> reverse(@AuthenticationPrincipal CurrentUser user,
            @PathVariable Long id, @RequestHeader(IdempotentCommand.HEADER) String idempotencyKey,
            @Valid @RequestBody ReverseRequest request) {
        return ApiResponse.ok(postingService.reverse(user, id, idempotencyKey, request));
    }
}
