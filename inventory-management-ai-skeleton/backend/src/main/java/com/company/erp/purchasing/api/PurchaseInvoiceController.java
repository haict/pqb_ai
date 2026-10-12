package com.company.erp.purchasing.api;

import com.company.erp.common.api.ApiResponse;
import com.company.erp.common.api.PageResponse;
import com.company.erp.common.application.IdempotentCommand;
import com.company.erp.purchasing.application.PurchaseInvoicePostingService;
import com.company.erp.purchasing.application.PurchaseInvoiceService;
import com.company.erp.purchasing.application.dto.CommentRequest;
import com.company.erp.purchasing.application.dto.InvoiceableLineResponse;
import com.company.erp.purchasing.application.dto.PurchaseInvoiceRequest;
import com.company.erp.purchasing.application.dto.PurchaseInvoiceResponse;
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
 * Hóa đơn mua (HDM, BR-PUR-09..11, 16, 17): theo dòng phiếu nhận hàng đã nhập kho và dịch vụ, chống trùng, ghi sổ
 * công nợ phải trả và bút toán, đảo.
 */
@Tag(name = "Hóa đơn mua")
@RestController
@RequestMapping("/api/v1/purchase-invoices")
public class PurchaseInvoiceController {

    private final PurchaseInvoiceService service;
    private final PurchaseInvoicePostingService postingService;

    public PurchaseInvoiceController(PurchaseInvoiceService service, PurchaseInvoicePostingService postingService) {
        this.service = service;
        this.postingService = postingService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('purchase-invoice:view')")
    public ApiResponse<PageResponse<PurchaseInvoiceResponse>> search(@AuthenticationPrincipal CurrentUser user,
            @RequestParam Long companyId, @RequestParam(required = false) Long branchId,
            @RequestParam(required = false) Long supplierId,
            @RequestParam(required = false) PurchaseDocumentStatus status,
            @RequestParam(required = false) LocalDate dateFrom, @RequestParam(required = false) LocalDate dateTo,
            @RequestParam(required = false) String keyword, Pageable pageable) {
        return ApiResponse.ok(PageResponse.from(service.search(user, new PurchaseInvoiceService.InvoiceFilter(
                companyId, branchId, status, supplierId, dateFrom, dateTo, keyword), pageable),
                Function.identity()));
    }

    @Operation(summary = "Dòng phiếu nhận chưa có hóa đơn", description = "Đã nhập kho, cùng chi nhánh và nhà cung cấp.")
    @GetMapping("/invoiceable-lines")
    @PreAuthorize("hasAuthority('purchase-invoice:view')")
    public ApiResponse<List<InvoiceableLineResponse>> invoiceableLines(@AuthenticationPrincipal CurrentUser user,
            @RequestParam Long branchId, @RequestParam Long supplierId) {
        return ApiResponse.ok(service.invoiceableLines(user, branchId, supplierId));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('purchase-invoice:view')")
    public ApiResponse<PurchaseInvoiceResponse> get(@AuthenticationPrincipal CurrentUser user, @PathVariable Long id) {
        return ApiResponse.ok(service.get(user, id));
    }

    @Operation(summary = "Lập hóa đơn mua", description = "Bắt buộc Idempotency-Key. Trùng (MST, ký hiệu, số): 409 "
            + "DUPLICATE_DOCUMENT; vượt số đã nhập chưa có hóa đơn: 422 SOURCE_QUANTITY_EXCEEDED.")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('purchase-invoice:create')")
    public ApiResponse<PurchaseInvoiceResponse> create(@AuthenticationPrincipal CurrentUser user,
            @RequestHeader(IdempotentCommand.HEADER) String idempotencyKey,
            @Valid @RequestBody PurchaseInvoiceRequest request) {
        return ApiResponse.ok(service.create(user, idempotencyKey, request));
    }

    @Operation(summary = "Sửa (DRAFT, REJECTED)", description = "Gửi version; đã đổi: 409 CONCURRENT_MODIFICATION.")
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('purchase-invoice:create')")
    public ApiResponse<PurchaseInvoiceResponse> update(@AuthenticationPrincipal CurrentUser user,
            @PathVariable Long id, @Valid @RequestBody PurchaseInvoiceRequest request) {
        return ApiResponse.ok(service.update(user, id, request));
    }

    @Operation(summary = "Gửi duyệt", description = "Duyệt theo luồng đã cấu hình; không có luồng thì tự duyệt.")
    @PostMapping("/{id}/submit")
    @PreAuthorize("hasAuthority('purchase-invoice:create')")
    public ApiResponse<PurchaseInvoiceResponse> submit(@AuthenticationPrincipal CurrentUser user,
            @PathVariable Long id, @RequestHeader(IdempotentCommand.HEADER) String idempotencyKey) {
        return ApiResponse.ok(service.submit(user, id, idempotencyKey));
    }

    @PostMapping("/{id}/approve")
    @PreAuthorize("hasAuthority('purchase-invoice:approve')")
    public ApiResponse<PurchaseInvoiceResponse> approve(@AuthenticationPrincipal CurrentUser user,
            @PathVariable Long id, @RequestHeader(IdempotentCommand.HEADER) String idempotencyKey,
            @Valid @RequestBody(required = false) CommentRequest request) {
        return ApiResponse.ok(service.approve(user, id, idempotencyKey, request));
    }

    @PostMapping("/{id}/reject")
    @PreAuthorize("hasAuthority('purchase-invoice:approve')")
    public ApiResponse<PurchaseInvoiceResponse> reject(@AuthenticationPrincipal CurrentUser user,
            @PathVariable Long id, @RequestHeader(IdempotentCommand.HEADER) String idempotencyKey,
            @Valid @RequestBody ReasonRequest request) {
        return ApiResponse.ok(service.reject(user, id, idempotencyKey, request));
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAuthority('purchase-invoice:create')")
    public ApiResponse<PurchaseInvoiceResponse> cancel(@AuthenticationPrincipal CurrentUser user,
            @PathVariable Long id, @RequestHeader(IdempotentCommand.HEADER) String idempotencyKey,
            @Valid @RequestBody ReasonRequest request) {
        return ApiResponse.ok(service.cancel(user, id, idempotencyKey, request));
    }

    @Operation(summary = "Ghi sổ", description = "Công nợ phải trả, bút toán theo quy tắc hạch toán HDM, chênh lệch giá "
            + "theo dòng phiếu nhận. Kỳ công nợ / kế toán khóa: 422 PERIOD_LOCKED; thiếu quy tắc: 422 "
            + "POSTING_RULE_NOT_FOUND.")
    @PostMapping("/{id}/post")
    @PreAuthorize("hasAuthority('purchase-invoice:post')")
    public ApiResponse<PurchaseInvoiceResponse> post(@AuthenticationPrincipal CurrentUser user, @PathVariable Long id,
            @RequestHeader(IdempotentCommand.HEADER) String idempotencyKey) {
        return ApiResponse.ok(postingService.post(user, id, idempotencyKey));
    }

    @Operation(summary = "Đảo", description = "Đảo công nợ, bút toán, chênh lệch giá; đã thanh toán / bù trừ: 409 "
            + "HAS_DEPENDENT_DOCUMENTS.")
    @PostMapping("/{id}/reverse")
    @PreAuthorize("hasAuthority('purchase-invoice:reverse')")
    public ApiResponse<PurchaseInvoiceResponse> reverse(@AuthenticationPrincipal CurrentUser user,
            @PathVariable Long id, @RequestHeader(IdempotentCommand.HEADER) String idempotencyKey,
            @Valid @RequestBody ReverseRequest request) {
        return ApiResponse.ok(postingService.reverse(user, id, idempotencyKey, request));
    }
}
