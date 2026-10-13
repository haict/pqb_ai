package com.company.erp.purchasing.api;

import com.company.erp.common.api.ApiResponse;
import com.company.erp.common.api.PageResponse;
import com.company.erp.common.application.IdempotentCommand;
import com.company.erp.purchasing.application.PurchaseOrderPostingService;
import com.company.erp.purchasing.application.PurchaseOrderService;
import com.company.erp.purchasing.application.PurchaseOrderSlipService;
import com.company.erp.purchasing.application.dto.CommentRequest;
import com.company.erp.purchasing.application.dto.PurchaseOrderRequest;
import com.company.erp.purchasing.application.dto.PurchaseOrderResponse;
import com.company.erp.purchasing.application.dto.StockInSlipResponse;
import com.company.erp.purchasing.application.dto.ReasonRequest;
import com.company.erp.purchasing.application.dto.ReverseRequest;
import com.company.erp.purchasing.domain.PurchaseDocumentStatus;
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
 * Đơn mua hàng (DMH, BR-PUR-04, D-54): chứng từ ghi sổ duy nhất của nghiệp vụ mua, mang hóa đơn của nhà cung cấp; duyệt
 * theo ngưỡng (cộng hàng đang về), ghi sổ nhập kho + công nợ phải trả + bút toán, đảo.
 */
@Tag(name = "Đơn mua hàng")
@RestController
@RequestMapping("/api/v1/purchase-orders")
public class PurchaseOrderController {

    private final PurchaseOrderService service;
    private final PurchaseOrderPostingService posting;
    private final PurchaseOrderSlipService slips;

    public PurchaseOrderController(PurchaseOrderService service, PurchaseOrderPostingService posting,
                                   PurchaseOrderSlipService slips) {
        this.service = service;
        this.posting = posting;
        this.slips = slips;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('purchase-order:view')")
    public ApiResponse<PageResponse<PurchaseOrderResponse>> search(@AuthenticationPrincipal CurrentUser user,
            @RequestParam Long companyId, @RequestParam(required = false) Long branchId,
            @RequestParam(required = false) Long supplierId,
            @RequestParam(required = false) Long warehouseId,
            @RequestParam(required = false) PurchaseDocumentStatus status,
            @RequestParam(required = false) LocalDate dateFrom, @RequestParam(required = false) LocalDate dateTo,
            @RequestParam(required = false) String keyword, Pageable pageable) {
        return ApiResponse.ok(PageResponse.from(service.search(user, new PurchaseOrderService.OrderFilter(companyId, branchId,
                status, supplierId, warehouseId,
                dateFrom, dateTo, keyword), pageable), Function.identity()));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('purchase-order:view')")
    public ApiResponse<PurchaseOrderResponse> get(@AuthenticationPrincipal CurrentUser user, @PathVariable Long id) {
        return ApiResponse.ok(service.get(user, id));
    }

    @Operation(summary = "In phiếu nhập kho",
            description = "Dữ liệu mẫu in phiếu nhập kho của đơn mua đã ghi sổ (chưa ghi sổ / đã đảo: 409 INVALID_DOCUMENT_STATE).")
    @GetMapping("/{id}/stock-in-slip")
    @PreAuthorize("hasAuthority('purchase-order:view')")
    public ApiResponse<StockInSlipResponse> stockInSlip(@AuthenticationPrincipal CurrentUser user,
                                                        @PathVariable Long id) {
        return ApiResponse.ok(slips.stockInSlip(user, id));
    }

    @Operation(summary = "Lập chứng từ", description = "Bắt buộc Idempotency-Key.")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('purchase-order:create')")
    public ApiResponse<PurchaseOrderResponse> create(@AuthenticationPrincipal CurrentUser user,
            @RequestHeader(IdempotentCommand.HEADER) String idempotencyKey,
            @Valid @RequestBody PurchaseOrderRequest request) {
        return ApiResponse.ok(service.create(user, idempotencyKey, request));
    }

    @Operation(summary = "Sửa (DRAFT, REJECTED)", description = "Gửi version; đã đổi: 409 CONCURRENT_MODIFICATION.")
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('purchase-order:create')")
    public ApiResponse<PurchaseOrderResponse> update(@AuthenticationPrincipal CurrentUser user, @PathVariable Long id,
            @Valid @RequestBody PurchaseOrderRequest request) {
        return ApiResponse.ok(service.update(user, id, request));
    }

    @Operation(summary = "Gửi duyệt", description = "Tổng tiền từ ngưỡng purchasing.order-approval-threshold trở lên phải duyệt (chưa cấu hình luồng: 422 APPROVAL_NOT_CONFIGURED); dưới ngưỡng tự duyệt. Duyệt xong cộng hàng đang về.")
    @PostMapping("/{id}/submit")
    @PreAuthorize("hasAuthority('purchase-order:create')")
    public ApiResponse<PurchaseOrderResponse> submit(@AuthenticationPrincipal CurrentUser user, @PathVariable Long id,
            @RequestHeader(IdempotentCommand.HEADER) String idempotencyKey) {
        return ApiResponse.ok(service.submit(user, id, idempotencyKey));
    }

    @PostMapping("/{id}/approve")
    @PreAuthorize("hasAuthority('purchase-order:approve')")
    public ApiResponse<PurchaseOrderResponse> approve(@AuthenticationPrincipal CurrentUser user, @PathVariable Long id,
            @RequestHeader(IdempotentCommand.HEADER) String idempotencyKey,
            @Valid @RequestBody(required = false) CommentRequest request) {
        return ApiResponse.ok(service.approve(user, id, idempotencyKey, request));
    }

    @PostMapping("/{id}/reject")
    @PreAuthorize("hasAuthority('purchase-order:approve')")
    public ApiResponse<PurchaseOrderResponse> reject(@AuthenticationPrincipal CurrentUser user, @PathVariable Long id,
            @RequestHeader(IdempotentCommand.HEADER) String idempotencyKey,
            @Valid @RequestBody ReasonRequest request) {
        return ApiResponse.ok(service.reject(user, id, idempotencyKey, request));
    }

    @Operation(summary = "Hủy", description = "Chỉ khi chưa ghi sổ; đơn đã duyệt thì trừ hàng đang về; bắt buộc lý do.")
    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAuthority('purchase-order:create')")
    public ApiResponse<PurchaseOrderResponse> cancel(@AuthenticationPrincipal CurrentUser user, @PathVariable Long id,
            @RequestHeader(IdempotentCommand.HEADER) String idempotencyKey,
            @Valid @RequestBody ReasonRequest request) {
        return ApiResponse.ok(service.cancel(user, id, idempotencyKey, request));
    }

    @Operation(summary = "Ghi sổ", description = "Đơn đã duyệt: phiếu nhập kho (lô, hạn dùng, serial), trừ hàng đang về, "
            + "công nợ phải trả, bút toán. Hóa đơn trùng: 409 DUPLICATE_DOCUMENT.")
    @PostMapping("/{id}/post")
    @PreAuthorize("hasAuthority('purchase-order:post')")
    public ApiResponse<PurchaseOrderResponse> post(@AuthenticationPrincipal CurrentUser user, @PathVariable Long id,
            @RequestHeader(IdempotentCommand.HEADER) String idempotencyKey) {
        return ApiResponse.ok(posting.post(user, id, idempotencyKey));
    }

    @Operation(summary = "Đảo", description = "Còn trả hàng / giảm giá / phân bổ chưa đảo, hàng đã xuất hoặc đã thanh "
            + "toán: 409 HAS_DEPENDENT_DOCUMENTS; bắt buộc lý do.")
    @PostMapping("/{id}/reverse")
    @PreAuthorize("hasAuthority('purchase-order:reverse')")
    public ApiResponse<PurchaseOrderResponse> reverse(@AuthenticationPrincipal CurrentUser user, @PathVariable Long id,
            @RequestHeader(IdempotentCommand.HEADER) String idempotencyKey,
            @Valid @RequestBody ReverseRequest request) {
        return ApiResponse.ok(posting.reverse(user, id, idempotencyKey, request));
    }
}
