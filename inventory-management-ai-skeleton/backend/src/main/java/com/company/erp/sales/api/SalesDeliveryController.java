package com.company.erp.sales.api;

import com.company.erp.common.api.ApiResponse;
import com.company.erp.common.api.PageResponse;
import com.company.erp.common.application.IdempotentCommand;
import com.company.erp.sales.application.SalesDeliveryPostingService;
import com.company.erp.sales.application.SalesDeliveryService;
import com.company.erp.sales.application.dto.CommentRequest;
import com.company.erp.sales.application.dto.ConfirmDeliveryRequest;
import com.company.erp.sales.application.dto.ReasonRequest;
import com.company.erp.sales.application.dto.ReverseRequest;
import com.company.erp.sales.application.dto.SalesDeliveryRequest;
import com.company.erp.sales.application.dto.SalesDeliveryResponse;
import com.company.erp.sales.application.dto.ShipRequest;
import com.company.erp.sales.domain.DeliveryStatus;
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
 * Phiếu giao hàng theo đơn bán (BR-SAL-20..23): xuất kho theo đơn (FEFO, dùng hàng đã giữ), đang giao, khách nhận
 * (biên bản giao nhận), giao thất bại (hàng về kho), đảo.
 */
@Tag(name = "Giao hàng")
@RestController
@RequestMapping("/api/v1/sales-deliveries")
public class SalesDeliveryController {

    private final SalesDeliveryService service;
    private final SalesDeliveryPostingService postingService;

    public SalesDeliveryController(SalesDeliveryService service, SalesDeliveryPostingService postingService) {
        this.service = service;
        this.postingService = postingService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('sales-delivery:view')")
    public ApiResponse<PageResponse<SalesDeliveryResponse>> search(@AuthenticationPrincipal CurrentUser user,
            @RequestParam Long companyId, @RequestParam(required = false) Long branchId,
            @RequestParam(required = false) Long orderId,
            @RequestParam(required = false) Long customerId,
            @RequestParam(required = false) SalesDocumentStatus status,
            @RequestParam(required = false) DeliveryStatus deliveryStatus,
            @RequestParam(required = false) LocalDate dateFrom, @RequestParam(required = false) LocalDate dateTo,
            @RequestParam(required = false) String keyword, Pageable pageable) {
        return ApiResponse.ok(PageResponse.from(service.search(user, new SalesDeliveryService.DeliveryFilter(companyId, branchId, status, deliveryStatus, orderId, customerId,
                dateFrom, dateTo, keyword), pageable),
                Function.identity()));
    }


    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('sales-delivery:view')")
    public ApiResponse<SalesDeliveryResponse> get(@AuthenticationPrincipal CurrentUser user, @PathVariable Long id) {
        return ApiResponse.ok(service.get(user, id));
    }

    @Operation(summary = "Lập", description = "Bắt buộc Idempotency-Key. Đơn bán phải đã duyệt, còn mở.")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('sales-delivery:create')")
    public ApiResponse<SalesDeliveryResponse> create(@AuthenticationPrincipal CurrentUser user,
            @RequestHeader(IdempotentCommand.HEADER) String idempotencyKey, @Valid @RequestBody SalesDeliveryRequest request) {
        return ApiResponse.ok(service.create(user, idempotencyKey, request));
    }

    @Operation(summary = "Sửa (DRAFT, REJECTED)", description = "Gửi version; đã đổi: 409 CONCURRENT_MODIFICATION.")
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('sales-delivery:create')")
    public ApiResponse<SalesDeliveryResponse> update(@AuthenticationPrincipal CurrentUser user, @PathVariable Long id,
            @Valid @RequestBody SalesDeliveryRequest request) {
        return ApiResponse.ok(service.update(user, id, request));
    }

    @Operation(summary = "Gửi duyệt", description = "Duyệt khi có luồng; không có luồng thì tự duyệt.")
    @PostMapping("/{id}/submit")
    @PreAuthorize("hasAuthority('sales-delivery:create')")
    public ApiResponse<SalesDeliveryResponse> submit(@AuthenticationPrincipal CurrentUser user,
            @PathVariable Long id, @RequestHeader(IdempotentCommand.HEADER) String idempotencyKey) {
        return ApiResponse.ok(service.submit(user, id, idempotencyKey));
    }

    @PostMapping("/{id}/approve")
    @PreAuthorize("hasAuthority('sales-delivery:approve')")
    public ApiResponse<SalesDeliveryResponse> approve(@AuthenticationPrincipal CurrentUser user,
            @PathVariable Long id, @RequestHeader(IdempotentCommand.HEADER) String idempotencyKey,
            @Valid @RequestBody(required = false) CommentRequest request) {
        return ApiResponse.ok(service.approve(user, id, idempotencyKey, request));
    }

    @PostMapping("/{id}/reject")
    @PreAuthorize("hasAuthority('sales-delivery:approve')")
    public ApiResponse<SalesDeliveryResponse> reject(@AuthenticationPrincipal CurrentUser user,
            @PathVariable Long id, @RequestHeader(IdempotentCommand.HEADER) String idempotencyKey,
            @Valid @RequestBody ReasonRequest request) {
        return ApiResponse.ok(service.reject(user, id, idempotencyKey, request));
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAuthority('sales-delivery:create')")
    public ApiResponse<SalesDeliveryResponse> cancel(@AuthenticationPrincipal CurrentUser user,
            @PathVariable Long id, @RequestHeader(IdempotentCommand.HEADER) String idempotencyKey,
            @Valid @RequestBody ReasonRequest request) {
        return ApiResponse.ok(service.cancel(user, id, idempotencyKey, request));
    }

    @Operation(summary = "Xuất kho", description = "Phiếu xuất PXK theo FEFO / lô chỉ định, dùng hàng đã giữ cho đơn. Vượt số còn phải xuất: 422 SOURCE_QUANTITY_EXCEEDED; thiếu tồn: 422 INSUFFICIENT_STOCK; lô dưới hạn dùng tối thiểu của khách: 422 LOT_NOT_SELLABLE.")
    @PostMapping("/{id}/post")
    @PreAuthorize("hasAuthority('sales-delivery:post')")
    public ApiResponse<SalesDeliveryResponse> post(@AuthenticationPrincipal CurrentUser user,
            @PathVariable Long id, @RequestHeader(IdempotentCommand.HEADER) String idempotencyKey) {
        return ApiResponse.ok(postingService.post(user, id, idempotencyKey));
    }

    @Operation(summary = "Đang giao")
    @PostMapping("/{id}/ship")
    @PreAuthorize("hasAuthority('sales-delivery:deliver')")
    public ApiResponse<SalesDeliveryResponse> ship(@AuthenticationPrincipal CurrentUser user,
            @PathVariable Long id, @RequestHeader(IdempotentCommand.HEADER) String idempotencyKey,
            @Valid @RequestBody ShipRequest request) {
        return ApiResponse.ok(postingService.ship(user, id, idempotencyKey, request));
    }

    @Operation(summary = "Khách nhận hàng")
    @PostMapping("/{id}/confirm")
    @PreAuthorize("hasAuthority('sales-delivery:deliver')")
    public ApiResponse<SalesDeliveryResponse> confirm(@AuthenticationPrincipal CurrentUser user,
            @PathVariable Long id, @RequestHeader(IdempotentCommand.HEADER) String idempotencyKey,
            @Valid @RequestBody ConfirmDeliveryRequest request) {
        return ApiResponse.ok(postingService.confirm(user, id, idempotencyKey, request));
    }

    @Operation(summary = "Giao thất bại", description = "Hàng quay về kho bằng chứng từ đảo phiếu xuất; hoàn số đã xuất của đơn và hàng đã giữ.")
    @PostMapping("/{id}/fail")
    @PreAuthorize("hasAuthority('sales-delivery:deliver')")
    public ApiResponse<SalesDeliveryResponse> fail(@AuthenticationPrincipal CurrentUser user,
            @PathVariable Long id, @RequestHeader(IdempotentCommand.HEADER) String idempotencyKey,
            @Valid @RequestBody ReverseRequest request) {
        return ApiResponse.ok(postingService.fail(user, id, idempotencyKey, request));
    }

    @Operation(summary = "Đảo", description = "Chưa giao, chưa xuất hóa đơn / trả hàng; đã có: 409 HAS_DEPENDENT_DOCUMENTS.")
    @PostMapping("/{id}/reverse")
    @PreAuthorize("hasAuthority('sales-delivery:reverse')")
    public ApiResponse<SalesDeliveryResponse> reverse(@AuthenticationPrincipal CurrentUser user,
            @PathVariable Long id, @RequestHeader(IdempotentCommand.HEADER) String idempotencyKey,
            @Valid @RequestBody ReverseRequest request) {
        return ApiResponse.ok(postingService.reverse(user, id, idempotencyKey, request));
    }
}
