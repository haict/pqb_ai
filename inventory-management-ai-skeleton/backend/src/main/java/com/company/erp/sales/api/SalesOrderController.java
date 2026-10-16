package com.company.erp.sales.api;

import com.company.erp.common.api.ApiResponse;
import com.company.erp.common.api.PageResponse;
import com.company.erp.common.application.IdempotentCommand;
import com.company.erp.sales.application.SalesOrderPostingService;
import com.company.erp.sales.application.SalesOrderService;
import com.company.erp.sales.application.SalesOrderSlipService;
import com.company.erp.sales.application.dto.CommentRequest;
import com.company.erp.sales.application.dto.ConfirmDeliveryRequest;
import com.company.erp.sales.application.dto.EinvoiceReferenceRequest;
import com.company.erp.sales.application.dto.PrintCompanyResponse;
import com.company.erp.sales.application.dto.ReasonRequest;
import com.company.erp.sales.application.dto.ReverseRequest;
import com.company.erp.sales.application.dto.SalesOrderRequest;
import com.company.erp.sales.application.dto.SalesOrderResponse;
import com.company.erp.sales.application.dto.ShipRequest;
import com.company.erp.sales.application.dto.StockOutSlipResponse;
import com.company.erp.sales.domain.SalesDocumentStatus;
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
 * Đơn bán (BR-SAL-10..14, 21..23, 30, 31, D-54): chứng từ ghi sổ duy nhất của nghiệp vụ bán. Duyệt giá / hạn mức tín
 * dụng, kiểm tra tồn khả dụng, giữ / nhả hàng; ghi sổ = xuất kho + doanh thu + công nợ phải thu + bút toán; số hóa đơn
 * điện tử; theo dõi giao hàng; đảo (kể cả giao thất bại); lịch thu tiền.
 */
@Tag(name = "Đơn bán")
@RestController
@RequestMapping("/api/v1/sales-orders")
public class SalesOrderController {

    private final SalesOrderService service;
    private final SalesOrderPostingService posting;
    private final SalesOrderSlipService slips;

    public SalesOrderController(SalesOrderService service, SalesOrderPostingService posting,
                                SalesOrderSlipService slips) {
        this.service = service;
        this.posting = posting;
        this.slips = slips;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('sales-order:view')")
    public ApiResponse<PageResponse<SalesOrderResponse>> search(@AuthenticationPrincipal CurrentUser user,
            @RequestParam Long companyId, @RequestParam(required = false) Long branchId,
            @RequestParam(required = false) Long customerId,
            @RequestParam(required = false) SalesDocumentStatus status,
            @RequestParam(required = false) LocalDate dateFrom, @RequestParam(required = false) LocalDate dateTo,
            @RequestParam(required = false) String keyword, Pageable pageable) {
        return ApiResponse.ok(PageResponse.from(service.search(user, new SalesOrderService.OrderFilter(companyId,
                branchId, status, customerId, dateFrom, dateTo, keyword), pageable), Function.identity()));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('sales-order:view')")
    public ApiResponse<SalesOrderResponse> get(@AuthenticationPrincipal CurrentUser user, @PathVariable Long id) {
        return ApiResponse.ok(service.get(user, id));
    }

    @Operation(summary = "In phiếu xuất kho",
            description = "Dữ liệu mẫu in phiếu xuất kho của đơn bán đã ghi sổ (chưa ghi sổ / đã đảo: 409 INVALID_DOCUMENT_STATE).")
    @GetMapping("/{id}/stock-out-slip")
    @PreAuthorize("hasAuthority('sales-order:view')")
    public ApiResponse<StockOutSlipResponse> stockOutSlip(@AuthenticationPrincipal CurrentUser user,
                                                          @PathVariable Long id,
                                                          @RequestParam(required = false) Long companyId) {
        return ApiResponse.ok(slips.stockOutSlip(user, id, companyId));
    }

    @Operation(summary = "Công ty in đầu phiếu",
            description = "Công ty đang hoạt động trong phạm vi người dùng, để chọn tên / địa chỉ in trên phiếu xuất kho.")
    @GetMapping("/print-companies")
    @PreAuthorize("hasAuthority('sales-order:view')")
    public ApiResponse<List<PrintCompanyResponse>> printCompanies(@AuthenticationPrincipal CurrentUser user) {
        return ApiResponse.ok(slips.printCompanies(user));
    }

    @Operation(summary = "Lập", description = "Bắt buộc Idempotency-Key. Đơn giá trống: lấy theo bảng giá (BR-SAL-02).")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('sales-order:create')")
    public ApiResponse<SalesOrderResponse> create(@AuthenticationPrincipal CurrentUser user,
            @RequestHeader(IdempotentCommand.HEADER) String idempotencyKey,
            @Valid @RequestBody SalesOrderRequest request) {
        return ApiResponse.ok(service.create(user, idempotencyKey, request));
    }

    @Operation(summary = "Sửa (DRAFT, REJECTED)", description = "Gửi version; đã đổi: 409 CONCURRENT_MODIFICATION.")
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('sales-order:create')")
    public ApiResponse<SalesOrderResponse> update(@AuthenticationPrincipal CurrentUser user, @PathVariable Long id,
            @Valid @RequestBody SalesOrderRequest request) {
        return ApiResponse.ok(service.update(user, id, request));
    }

    @Operation(summary = "Gửi duyệt", description = "Giá dưới sàn, chiết khấu vượt ngưỡng, vượt hạn mức tín dụng hoặc có "
            + "nợ quá hạn: phải duyệt (lý do ở approvalReasons); không vi phạm thì tự duyệt. Giữ hàng khi duyệt nếu "
            + "reserveStock (thiếu: 422 INSUFFICIENT_STOCK).")
    @PostMapping("/{id}/submit")
    @PreAuthorize("hasAuthority('sales-order:create')")
    public ApiResponse<SalesOrderResponse> submit(@AuthenticationPrincipal CurrentUser user, @PathVariable Long id,
            @RequestHeader(IdempotentCommand.HEADER) String idempotencyKey) {
        return ApiResponse.ok(service.submit(user, id, idempotencyKey));
    }

    @PostMapping("/{id}/approve")
    @PreAuthorize("hasAuthority('sales-order:approve')")
    public ApiResponse<SalesOrderResponse> approve(@AuthenticationPrincipal CurrentUser user, @PathVariable Long id,
            @RequestHeader(IdempotentCommand.HEADER) String idempotencyKey,
            @Valid @RequestBody(required = false) CommentRequest request) {
        return ApiResponse.ok(service.approve(user, id, idempotencyKey, request));
    }

    @PostMapping("/{id}/reject")
    @PreAuthorize("hasAuthority('sales-order:approve')")
    public ApiResponse<SalesOrderResponse> reject(@AuthenticationPrincipal CurrentUser user, @PathVariable Long id,
            @RequestHeader(IdempotentCommand.HEADER) String idempotencyKey,
            @Valid @RequestBody ReasonRequest request) {
        return ApiResponse.ok(service.reject(user, id, idempotencyKey, request));
    }

    @Operation(summary = "Hủy", description = "Chỉ khi chưa ghi sổ; nhả hàng đang giữ; bắt buộc lý do.")
    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAuthority('sales-order:create')")
    public ApiResponse<SalesOrderResponse> cancel(@AuthenticationPrincipal CurrentUser user, @PathVariable Long id,
            @RequestHeader(IdempotentCommand.HEADER) String idempotencyKey,
            @Valid @RequestBody ReasonRequest request) {
        return ApiResponse.ok(service.cancel(user, id, idempotencyKey, request));
    }

    @Operation(summary = "Giữ hàng", description = "Đơn đã duyệt: giữ dòng hàng hóa, hết hạn sau sales.reservation-days "
            + "(D-37).")
    @PostMapping("/{id}/reserve")
    @PreAuthorize("hasAuthority('sales-order:create')")
    public ApiResponse<SalesOrderResponse> reserve(@AuthenticationPrincipal CurrentUser user, @PathVariable Long id,
            @RequestHeader(IdempotentCommand.HEADER) String idempotencyKey) {
        return ApiResponse.ok(service.reserve(user, id, idempotencyKey));
    }

    @Operation(summary = "Nhả hàng")
    @PostMapping("/{id}/release")
    @PreAuthorize("hasAuthority('sales-order:create')")
    public ApiResponse<SalesOrderResponse> release(@AuthenticationPrincipal CurrentUser user, @PathVariable Long id,
            @RequestHeader(IdempotentCommand.HEADER) String idempotencyKey) {
        return ApiResponse.ok(service.release(user, id, idempotencyKey));
    }

    @Operation(summary = "Ghi sổ", description = "Đơn đã duyệt: phiếu xuất kho (FEFO / lô chỉ định, dùng hàng đã giữ, hạn "
            + "dùng tối thiểu của khách D-07), doanh thu, công nợ phải thu, bút toán.")
    @PostMapping("/{id}/post")
    @PreAuthorize("hasAuthority('sales-order:post')")
    public ApiResponse<SalesOrderResponse> post(@AuthenticationPrincipal CurrentUser user, @PathVariable Long id,
            @RequestHeader(IdempotentCommand.HEADER) String idempotencyKey) {
        return ApiResponse.ok(posting.post(user, id, idempotencyKey));
    }

    @Operation(summary = "Ghi số hóa đơn điện tử", description = "BR-SAL-31: số đã phát hành trên MISA, trước hoặc sau "
            + "ghi sổ; trùng: 409 DUPLICATE_DOCUMENT.")
    @PostMapping("/{id}/einvoice-reference")
    @PreAuthorize("hasAuthority('sales-order:post')")
    public ApiResponse<SalesOrderResponse> recordEinvoice(@AuthenticationPrincipal CurrentUser user,
            @PathVariable Long id, @RequestHeader(IdempotentCommand.HEADER) String idempotencyKey,
            @Valid @RequestBody EinvoiceReferenceRequest request) {
        return ApiResponse.ok(posting.recordEinvoice(user, id, idempotencyKey, request));
    }

    @Operation(summary = "Đang giao", description = "Đơn đã ghi sổ (đã xuất kho).")
    @PostMapping("/{id}/ship")
    @PreAuthorize("hasAuthority('sales-order:deliver')")
    public ApiResponse<SalesOrderResponse> ship(@AuthenticationPrincipal CurrentUser user, @PathVariable Long id,
            @RequestHeader(IdempotentCommand.HEADER) String idempotencyKey,
            @Valid @RequestBody(required = false) ShipRequest request) {
        return ApiResponse.ok(posting.ship(user, id, idempotencyKey, request));
    }

    @Operation(summary = "Khách nhận hàng", description = "BR-SAL-22: ngày nhận, người nhận, số biên bản đã ký.")
    @PostMapping("/{id}/confirm")
    @PreAuthorize("hasAuthority('sales-order:deliver')")
    public ApiResponse<SalesOrderResponse> confirm(@AuthenticationPrincipal CurrentUser user, @PathVariable Long id,
            @RequestHeader(IdempotentCommand.HEADER) String idempotencyKey,
            @Valid @RequestBody ConfirmDeliveryRequest request) {
        return ApiResponse.ok(posting.confirm(user, id, idempotencyKey, request));
    }

    @Operation(summary = "Đảo / giao thất bại", description = "Hàng về kho, đảo doanh thu và công nợ; còn trả hàng / giảm "
            + "giá chưa đảo, hàng đã xuất tiếp hoặc đã thu tiền: 409 HAS_DEPENDENT_DOCUMENTS; bắt buộc lý do.")
    @PostMapping("/{id}/reverse")
    @PreAuthorize("hasAuthority('sales-order:reverse')")
    public ApiResponse<SalesOrderResponse> reverse(@AuthenticationPrincipal CurrentUser user, @PathVariable Long id,
            @RequestHeader(IdempotentCommand.HEADER) String idempotencyKey,
            @Valid @RequestBody ReverseRequest request) {
        return ApiResponse.ok(posting.reverse(user, id, idempotencyKey, request));
    }
}
