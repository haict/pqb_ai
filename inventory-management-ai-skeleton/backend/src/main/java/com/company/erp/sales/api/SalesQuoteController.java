package com.company.erp.sales.api;

import com.company.erp.common.api.ApiResponse;
import com.company.erp.common.api.PageResponse;
import com.company.erp.common.application.IdempotentCommand;
import com.company.erp.sales.application.SalesOrderService;
import com.company.erp.sales.application.SalesQuoteService;
import com.company.erp.sales.application.dto.CommentRequest;
import com.company.erp.sales.application.dto.QuoteToOrderRequest;
import com.company.erp.sales.application.dto.ReasonRequest;
import com.company.erp.sales.application.dto.SalesOrderResponse;
import com.company.erp.sales.application.dto.SalesQuoteRequest;
import com.company.erp.sales.application.dto.SalesQuoteResponse;
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
 * Báo giá (BR-SAL-01..05): lập theo giá ưu tiên, duyệt giá (dưới sàn / chiết khấu vượt ngưỡng), gửi khách, khách chấp
 * nhận / từ chối, phiên bản mới, chuyển thành đơn bán.
 */
@Tag(name = "Báo giá")
@RestController
@RequestMapping("/api/v1/sales-quotes")
public class SalesQuoteController {

    private final SalesQuoteService service;
    private final SalesOrderService orderService;

    public SalesQuoteController(SalesQuoteService service, SalesOrderService orderService) {
        this.service = service;
        this.orderService = orderService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('sales-quote:view')")
    public ApiResponse<PageResponse<SalesQuoteResponse>> search(@AuthenticationPrincipal CurrentUser user,
            @RequestParam Long companyId, @RequestParam(required = false) Long branchId,
            @RequestParam(required = false) Long customerId,
            @RequestParam(required = false) SalesDocumentStatus status,
            @RequestParam(required = false) LocalDate dateFrom, @RequestParam(required = false) LocalDate dateTo,
            @RequestParam(required = false) String keyword, Pageable pageable) {
        return ApiResponse.ok(PageResponse.from(service.search(user, new SalesQuoteService.QuoteFilter(companyId, branchId, status, customerId, dateFrom, dateTo, keyword), pageable),
                Function.identity()));
    }


    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('sales-quote:view')")
    public ApiResponse<SalesQuoteResponse> get(@AuthenticationPrincipal CurrentUser user, @PathVariable Long id) {
        return ApiResponse.ok(service.get(user, id));
    }

    @Operation(summary = "Lập", description = "Bắt buộc Idempotency-Key. Đơn giá trống: lấy theo bảng giá (BR-SAL-02); không có giá: 400.")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('sales-quote:create')")
    public ApiResponse<SalesQuoteResponse> create(@AuthenticationPrincipal CurrentUser user,
            @RequestHeader(IdempotentCommand.HEADER) String idempotencyKey, @Valid @RequestBody SalesQuoteRequest request) {
        return ApiResponse.ok(service.create(user, idempotencyKey, request));
    }

    @Operation(summary = "Sửa (DRAFT, REJECTED)", description = "Gửi version; đã đổi: 409 CONCURRENT_MODIFICATION.")
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('sales-quote:create')")
    public ApiResponse<SalesQuoteResponse> update(@AuthenticationPrincipal CurrentUser user, @PathVariable Long id,
            @Valid @RequestBody SalesQuoteRequest request) {
        return ApiResponse.ok(service.update(user, id, request));
    }

    @Operation(summary = "Gửi duyệt", description = "Giá dưới giá sàn / chiết khấu vượt ngưỡng: phải duyệt (chưa có luồng: 422 APPROVAL_NOT_CONFIGURED); không vi phạm thì tự duyệt.")
    @PostMapping("/{id}/submit")
    @PreAuthorize("hasAuthority('sales-quote:create')")
    public ApiResponse<SalesQuoteResponse> submit(@AuthenticationPrincipal CurrentUser user,
            @PathVariable Long id, @RequestHeader(IdempotentCommand.HEADER) String idempotencyKey) {
        return ApiResponse.ok(service.submit(user, id, idempotencyKey));
    }

    @PostMapping("/{id}/approve")
    @PreAuthorize("hasAuthority('sales-quote:approve')")
    public ApiResponse<SalesQuoteResponse> approve(@AuthenticationPrincipal CurrentUser user,
            @PathVariable Long id, @RequestHeader(IdempotentCommand.HEADER) String idempotencyKey,
            @Valid @RequestBody(required = false) CommentRequest request) {
        return ApiResponse.ok(service.approve(user, id, idempotencyKey, request));
    }

    @PostMapping("/{id}/reject")
    @PreAuthorize("hasAuthority('sales-quote:approve')")
    public ApiResponse<SalesQuoteResponse> reject(@AuthenticationPrincipal CurrentUser user,
            @PathVariable Long id, @RequestHeader(IdempotentCommand.HEADER) String idempotencyKey,
            @Valid @RequestBody ReasonRequest request) {
        return ApiResponse.ok(service.reject(user, id, idempotencyKey, request));
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAuthority('sales-quote:create')")
    public ApiResponse<SalesQuoteResponse> cancel(@AuthenticationPrincipal CurrentUser user,
            @PathVariable Long id, @RequestHeader(IdempotentCommand.HEADER) String idempotencyKey,
            @Valid @RequestBody ReasonRequest request) {
        return ApiResponse.ok(service.cancel(user, id, idempotencyKey, request));
    }

    @Operation(summary = "Đã gửi khách", description = "Chỉ báo giá đã duyệt.")
    @PostMapping("/{id}/send")
    @PreAuthorize("hasAuthority('sales-quote:create')")
    public ApiResponse<SalesQuoteResponse> markSent(@AuthenticationPrincipal CurrentUser user,
            @PathVariable Long id, @RequestHeader(IdempotentCommand.HEADER) String idempotencyKey) {
        return ApiResponse.ok(service.markSent(user, id, idempotencyKey));
    }

    @Operation(summary = "Khách chấp nhận", description = "Hết hiệu lực: 409 INVALID_DOCUMENT_STATE.")
    @PostMapping("/{id}/accept")
    @PreAuthorize("hasAuthority('sales-quote:create')")
    public ApiResponse<SalesQuoteResponse> accept(@AuthenticationPrincipal CurrentUser user,
            @PathVariable Long id, @RequestHeader(IdempotentCommand.HEADER) String idempotencyKey) {
        return ApiResponse.ok(service.accept(user, id, idempotencyKey));
    }

    @Operation(summary = "Khách từ chối")
    @PostMapping("/{id}/decline")
    @PreAuthorize("hasAuthority('sales-quote:create')")
    public ApiResponse<SalesQuoteResponse> decline(@AuthenticationPrincipal CurrentUser user,
            @PathVariable Long id, @RequestHeader(IdempotentCommand.HEADER) String idempotencyKey,
            @Valid @RequestBody ReasonRequest request) {
        return ApiResponse.ok(service.decline(user, id, idempotencyKey, request));
    }

    @Operation(summary = "Phiên bản mới", description = "Sao thành báo giá nháp mới, phiên bản cũ đóng lại.")
    @PostMapping("/{id}/revise")
    @PreAuthorize("hasAuthority('sales-quote:create')")
    public ApiResponse<SalesQuoteResponse> revise(@AuthenticationPrincipal CurrentUser user,
            @PathVariable Long id, @RequestHeader(IdempotentCommand.HEADER) String idempotencyKey) {
        return ApiResponse.ok(service.revise(user, id, idempotencyKey));
    }

    @Operation(summary = "Chuyển thành đơn bán", description = "Báo giá khách đã chấp nhận; mỗi báo giá một đơn.")
    @PostMapping("/{id}/create-order")
    @PreAuthorize("hasAuthority('sales-order:create')")
    public ApiResponse<SalesOrderResponse> createFromQuote(@AuthenticationPrincipal CurrentUser user,
            @PathVariable Long id, @RequestHeader(IdempotentCommand.HEADER) String idempotencyKey,
            @Valid @RequestBody QuoteToOrderRequest request) {
        return ApiResponse.ok(orderService.createFromQuote(user, id, idempotencyKey, request));
    }
}
