package com.company.erp.purchasing.api;

import com.company.erp.common.api.ApiResponse;
import com.company.erp.common.api.PageResponse;
import com.company.erp.common.application.IdempotentCommand;
import com.company.erp.purchasing.application.PurchaseReceiptPostingService;
import com.company.erp.purchasing.application.PurchaseReceiptService;
import com.company.erp.purchasing.application.dto.CommentRequest;
import com.company.erp.purchasing.application.dto.PurchaseReceiptRequest;
import com.company.erp.purchasing.application.dto.PurchaseReceiptResponse;
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
 * Phiếu nhận hàng (PNH, BR-PUR-07, BR-PUR-08, D-36): nhận theo đơn mua, kiểm nhận (đạt / cách ly / trả lại), nhập kho
 * thành phiếu nhập kho riêng, thao tác nhanh nhận và nhập trong một lần, đảo.
 */
@Tag(name = "Phiếu nhận hàng")
@RestController
@RequestMapping("/api/v1/purchase-receipts")
public class PurchaseReceiptController {

    private final PurchaseReceiptService service;
    private final PurchaseReceiptPostingService postingService;

    public PurchaseReceiptController(PurchaseReceiptService service, PurchaseReceiptPostingService postingService) {
        this.service = service;
        this.postingService = postingService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('purchase-receipt:view')")
    public ApiResponse<PageResponse<PurchaseReceiptResponse>> search(@AuthenticationPrincipal CurrentUser user,
            @RequestParam Long companyId, @RequestParam(required = false) Long branchId,
            @RequestParam(required = false) Long purchaseOrderId,
            @RequestParam(required = false) PurchaseDocumentStatus status,
            @RequestParam(required = false) LocalDate dateFrom, @RequestParam(required = false) LocalDate dateTo,
            @RequestParam(required = false) String keyword, Pageable pageable) {
        return ApiResponse.ok(PageResponse.from(service.search(user, new PurchaseReceiptService.ReceiptFilter(
                companyId, branchId, status, purchaseOrderId, dateFrom, dateTo, keyword), pageable),
                Function.identity()));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('purchase-receipt:view')")
    public ApiResponse<PurchaseReceiptResponse> get(@AuthenticationPrincipal CurrentUser user, @PathVariable Long id) {
        return ApiResponse.ok(service.get(user, id));
    }

    @Operation(summary = "Lập phiếu nhận hàng", description = "Bắt buộc Idempotency-Key.")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('purchase-receipt:create')")
    public ApiResponse<PurchaseReceiptResponse> create(@AuthenticationPrincipal CurrentUser user,
            @RequestHeader(IdempotentCommand.HEADER) String idempotencyKey,
            @Valid @RequestBody PurchaseReceiptRequest request) {
        return ApiResponse.ok(service.create(user, idempotencyKey, request));
    }

    @Operation(summary = "Nhận hàng và nhập kho (D-36)", description = "Lập, gửi duyệt; không vượt dung sai thì ghi "
            + "sổ và nhập kho ngay, vượt thì dừng ở chờ duyệt.")
    @PostMapping("/receive-and-stock")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('purchase-receipt:post')")
    public ApiResponse<PurchaseReceiptResponse> receiveAndStock(@AuthenticationPrincipal CurrentUser user,
            @RequestHeader(IdempotentCommand.HEADER) String idempotencyKey,
            @Valid @RequestBody PurchaseReceiptRequest request) {
        return ApiResponse.ok(postingService.receiveAndStock(user, idempotencyKey, request));
    }

    @Operation(summary = "Sửa (DRAFT, REJECTED)", description = "Gửi version; đã đổi: 409 CONCURRENT_MODIFICATION.")
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('purchase-receipt:create')")
    public ApiResponse<PurchaseReceiptResponse> update(@AuthenticationPrincipal CurrentUser user,
            @PathVariable Long id, @Valid @RequestBody PurchaseReceiptRequest request) {
        return ApiResponse.ok(service.update(user, id, request));
    }

    @Operation(summary = "Gửi duyệt", description = "Nhận vượt số đặt quá dung sai purchasing.receipt-over-tolerance-"
            + "percent: phải duyệt theo luồng (chưa có luồng: 422 APPROVAL_NOT_CONFIGURED); không vượt: tự duyệt.")
    @PostMapping("/{id}/submit")
    @PreAuthorize("hasAuthority('purchase-receipt:create')")
    public ApiResponse<PurchaseReceiptResponse> submit(@AuthenticationPrincipal CurrentUser user,
            @PathVariable Long id, @RequestHeader(IdempotentCommand.HEADER) String idempotencyKey) {
        return ApiResponse.ok(service.submit(user, id, idempotencyKey));
    }

    @PostMapping("/{id}/approve")
    @PreAuthorize("hasAuthority('purchase-receipt:approve')")
    public ApiResponse<PurchaseReceiptResponse> approve(@AuthenticationPrincipal CurrentUser user,
            @PathVariable Long id, @RequestHeader(IdempotentCommand.HEADER) String idempotencyKey,
            @Valid @RequestBody(required = false) CommentRequest request) {
        return ApiResponse.ok(service.approve(user, id, idempotencyKey, request));
    }

    @PostMapping("/{id}/reject")
    @PreAuthorize("hasAuthority('purchase-receipt:approve')")
    public ApiResponse<PurchaseReceiptResponse> reject(@AuthenticationPrincipal CurrentUser user,
            @PathVariable Long id, @RequestHeader(IdempotentCommand.HEADER) String idempotencyKey,
            @Valid @RequestBody ReasonRequest request) {
        return ApiResponse.ok(service.reject(user, id, idempotencyKey, request));
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAuthority('purchase-receipt:create')")
    public ApiResponse<PurchaseReceiptResponse> cancel(@AuthenticationPrincipal CurrentUser user,
            @PathVariable Long id, @RequestHeader(IdempotentCommand.HEADER) String idempotencyKey,
            @Valid @RequestBody ReasonRequest request) {
        return ApiResponse.ok(service.cancel(user, id, idempotencyKey, request));
    }

    @Operation(summary = "Ghi sổ (xác nhận nhận hàng)", description = "Cộng số đã nhận / lỗi của đơn mua; không đổi "
            + "tồn. Đơn mua đã đóng / hoàn thành: 409 INVALID_DOCUMENT_STATE.")
    @PostMapping("/{id}/post")
    @PreAuthorize("hasAuthority('purchase-receipt:post')")
    public ApiResponse<PurchaseReceiptResponse> post(@AuthenticationPrincipal CurrentUser user, @PathVariable Long id,
            @RequestHeader(IdempotentCommand.HEADER) String idempotencyKey) {
        return ApiResponse.ok(postingService.post(user, id, idempotencyKey));
    }

    @Operation(summary = "Nhập kho", description = "Lập và ghi sổ phiếu nhập kho cho dòng đạt / cách ly; trừ hàng đang "
            + "về. Kỳ kho khóa: 422 PERIOD_LOCKED.")
    @PostMapping("/{id}/stock-in")
    @PreAuthorize("hasAuthority('purchase-receipt:post')")
    public ApiResponse<PurchaseReceiptResponse> stockIn(@AuthenticationPrincipal CurrentUser user,
            @PathVariable Long id, @RequestHeader(IdempotentCommand.HEADER) String idempotencyKey) {
        return ApiResponse.ok(postingService.stockIn(user, id, idempotencyKey));
    }

    @Operation(summary = "Đảo", description = "Đảo cả phiếu nhập kho; hàng đã xuất: 409 HAS_DEPENDENT_DOCUMENTS.")
    @PostMapping("/{id}/reverse")
    @PreAuthorize("hasAuthority('purchase-receipt:reverse')")
    public ApiResponse<PurchaseReceiptResponse> reverse(@AuthenticationPrincipal CurrentUser user,
            @PathVariable Long id, @RequestHeader(IdempotentCommand.HEADER) String idempotencyKey,
            @Valid @RequestBody ReverseRequest request) {
        return ApiResponse.ok(postingService.reverse(user, id, idempotencyKey, request));
    }
}
