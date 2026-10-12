package com.company.erp.sales.api;

import com.company.erp.common.api.ApiResponse;
import com.company.erp.common.api.PageResponse;
import com.company.erp.common.application.IdempotentCommand;
import com.company.erp.sales.application.SalesOrderService;
import com.company.erp.sales.application.dto.CommentRequest;
import com.company.erp.sales.application.dto.PurchaseRequestResult;
import com.company.erp.sales.application.dto.ReasonRequest;
import com.company.erp.sales.application.dto.SalesOrderRequest;
import com.company.erp.sales.application.dto.SalesOrderResponse;
import com.company.erp.sales.domain.SalesDocumentStatus;
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

/**
 * Đơn bán (BR-SAL-10..15): duyệt giá / hạn mức tín dụng, kiểm tra tồn khả dụng, giữ / nhả hàng, đóng phần còn lại,
 * đề nghị mua phần thiếu, tiến độ xuất / giao / hóa đơn / trả lại, lịch thu tiền.
 */
@Tag(name = "Đơn bán")
@RestController
@RequestMapping("/api/v1/sales-orders")
public class SalesOrderController {

    private final SalesOrderService service;

    public SalesOrderController(SalesOrderService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('sales-order:view')")
    public ApiResponse<PageResponse<SalesOrderResponse>> search(@AuthenticationPrincipal CurrentUser user,
            @RequestParam Long companyId, @RequestParam(required = false) Long branchId,
            @RequestParam(required = false) Long customerId,
            @RequestParam(required = false) SalesDocumentStatus status,
            @RequestParam(defaultValue = "false") boolean openOnly,
            @RequestParam(required = false) LocalDate dateFrom, @RequestParam(required = false) LocalDate dateTo,
            @RequestParam(required = false) String keyword, Pageable pageable) {
        return ApiResponse.ok(PageResponse.from(service.search(user, new SalesOrderService.OrderFilter(companyId, branchId, status, customerId, openOnly, dateFrom, dateTo,
                keyword), pageable),
                Function.identity()));
    }


    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('sales-order:view')")
    public ApiResponse<SalesOrderResponse> get(@AuthenticationPrincipal CurrentUser user, @PathVariable Long id) {
        return ApiResponse.ok(service.get(user, id));
    }

    @Operation(summary = "Lập", description = "Bắt buộc Idempotency-Key. Đơn giá trống: lấy theo bảng giá (BR-SAL-02).")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('sales-order:create')")
    public ApiResponse<SalesOrderResponse> create(@AuthenticationPrincipal CurrentUser user,
            @RequestHeader(IdempotentCommand.HEADER) String idempotencyKey, @Valid @RequestBody SalesOrderRequest request) {
        return ApiResponse.ok(service.create(user, idempotencyKey, request));
    }

    @Operation(summary = "Sửa (DRAFT, REJECTED)", description = "Gửi version; đã đổi: 409 CONCURRENT_MODIFICATION.")
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('sales-order:create')")
    public ApiResponse<SalesOrderResponse> update(@AuthenticationPrincipal CurrentUser user, @PathVariable Long id,
            @Valid @RequestBody SalesOrderRequest request) {
        return ApiResponse.ok(service.update(user, id, request));
    }

    @Operation(summary = "Gửi duyệt", description = "Giá dưới sàn, chiết khấu vượt ngưỡng, vượt hạn mức tín dụng hoặc có nợ quá hạn: phải duyệt (lý do ở approvalReasons); không vi phạm thì tự duyệt. Giữ hàng khi duyệt nếu reserveStock (thiếu: 422 INSUFFICIENT_STOCK).")
    @PostMapping("/{id}/submit")
    @PreAuthorize("hasAuthority('sales-order:create')")
    public ApiResponse<SalesOrderResponse> submit(@AuthenticationPrincipal CurrentUser user,
            @PathVariable Long id, @RequestHeader(IdempotentCommand.HEADER) String idempotencyKey) {
        return ApiResponse.ok(service.submit(user, id, idempotencyKey));
    }

    @PostMapping("/{id}/approve")
    @PreAuthorize("hasAuthority('sales-order:approve')")
    public ApiResponse<SalesOrderResponse> approve(@AuthenticationPrincipal CurrentUser user,
            @PathVariable Long id, @RequestHeader(IdempotentCommand.HEADER) String idempotencyKey,
            @Valid @RequestBody(required = false) CommentRequest request) {
        return ApiResponse.ok(service.approve(user, id, idempotencyKey, request));
    }

    @PostMapping("/{id}/reject")
    @PreAuthorize("hasAuthority('sales-order:approve')")
    public ApiResponse<SalesOrderResponse> reject(@AuthenticationPrincipal CurrentUser user,
            @PathVariable Long id, @RequestHeader(IdempotentCommand.HEADER) String idempotencyKey,
            @Valid @RequestBody ReasonRequest request) {
        return ApiResponse.ok(service.reject(user, id, idempotencyKey, request));
    }

    @Operation(summary = "Hủy", description = "Chưa phát sinh xuất kho / hóa đơn; nhả hàng đang giữ.")
    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAuthority('sales-order:create')")
    public ApiResponse<SalesOrderResponse> cancel(@AuthenticationPrincipal CurrentUser user,
            @PathVariable Long id, @RequestHeader(IdempotentCommand.HEADER) String idempotencyKey,
            @Valid @RequestBody ReasonRequest request) {
        return ApiResponse.ok(service.cancel(user, id, idempotencyKey, request));
    }

    @Operation(summary = "Đóng phần còn lại")
    @PostMapping("/{id}/close")
    @PreAuthorize("hasAuthority('sales-order:close')")
    public ApiResponse<SalesOrderResponse> close(@AuthenticationPrincipal CurrentUser user,
            @PathVariable Long id, @RequestHeader(IdempotentCommand.HEADER) String idempotencyKey,
            @Valid @RequestBody ReasonRequest request) {
        return ApiResponse.ok(service.close(user, id, idempotencyKey, request));
    }

    @Operation(summary = "Giữ hàng", description = "Giữ lại cho phần còn phải xuất, hết hạn sau sales.reservation-days (D-37).")
    @PostMapping("/{id}/reserve")
    @PreAuthorize("hasAuthority('sales-order:create')")
    public ApiResponse<SalesOrderResponse> reserve(@AuthenticationPrincipal CurrentUser user,
            @PathVariable Long id, @RequestHeader(IdempotentCommand.HEADER) String idempotencyKey) {
        return ApiResponse.ok(service.reserve(user, id, idempotencyKey));
    }

    @Operation(summary = "Nhả hàng")
    @PostMapping("/{id}/release")
    @PreAuthorize("hasAuthority('sales-order:create')")
    public ApiResponse<SalesOrderResponse> release(@AuthenticationPrincipal CurrentUser user,
            @PathVariable Long id, @RequestHeader(IdempotentCommand.HEADER) String idempotencyKey) {
        return ApiResponse.ok(service.release(user, id, idempotencyKey));
    }

    @Operation(summary = "Đề nghị mua phần thiếu", description = "BR-SAL-15.")
    @PostMapping("/{id}/create-purchase-request")
    @PreAuthorize("hasAuthority('sales-order:create')")
    public ApiResponse<PurchaseRequestResult> createPurchaseRequest(@AuthenticationPrincipal CurrentUser user,
            @PathVariable Long id, @RequestHeader(IdempotentCommand.HEADER) String idempotencyKey) {
        return ApiResponse.ok(service.createPurchaseRequest(user, id, idempotencyKey));
    }
}
