package com.company.erp.sales.api;

import com.company.erp.common.api.ApiResponse;
import com.company.erp.common.api.PageResponse;
import com.company.erp.common.application.IdempotentCommand;
import com.company.erp.sales.application.SalesInvoicePostingService;
import com.company.erp.sales.application.SalesInvoiceService;
import com.company.erp.sales.application.dto.CommentRequest;
import com.company.erp.sales.application.dto.EinvoiceReferenceRequest;
import com.company.erp.sales.application.dto.InvoiceableSalesLineResponse;
import com.company.erp.sales.application.dto.ReasonRequest;
import com.company.erp.sales.application.dto.ReverseRequest;
import com.company.erp.sales.application.dto.SalesInvoiceRequest;
import com.company.erp.sales.application.dto.SalesInvoiceResponse;
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
 * Hóa đơn bán (BR-SAL-30, 31, D-35): từ dòng phiếu giao đã xuất kho và dòng dịch vụ của đơn bán; số hóa đơn điện tử
 * từ MISA; ghi sổ công nợ phải thu và doanh thu; đảo.
 */
@Tag(name = "Hóa đơn bán")
@RestController
@RequestMapping("/api/v1/sales-invoices")
public class SalesInvoiceController {

    private final SalesInvoiceService service;
    private final SalesInvoicePostingService postingService;

    public SalesInvoiceController(SalesInvoiceService service, SalesInvoicePostingService postingService) {
        this.service = service;
        this.postingService = postingService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('sales-invoice:view')")
    public ApiResponse<PageResponse<SalesInvoiceResponse>> search(@AuthenticationPrincipal CurrentUser user,
            @RequestParam Long companyId, @RequestParam(required = false) Long branchId,
            @RequestParam(required = false) Long customerId,
            @RequestParam(required = false) SalesDocumentStatus status,
            @RequestParam(required = false) LocalDate dateFrom, @RequestParam(required = false) LocalDate dateTo,
            @RequestParam(required = false) String keyword, Pageable pageable) {
        return ApiResponse.ok(PageResponse.from(service.search(user, new SalesInvoiceService.InvoiceFilter(companyId, branchId, status, customerId, dateFrom, dateTo, keyword), pageable),
                Function.identity()));
    }

    @Operation(summary = "Dòng còn xuất hóa đơn được", description = "Dòng phiếu giao đã xuất kho và dòng dịch vụ của đơn bán.")
    @GetMapping("/invoiceable-lines")
    @PreAuthorize("hasAuthority('sales-invoice:view')")
    public ApiResponse<List<InvoiceableSalesLineResponse>> invoiceableLines(@AuthenticationPrincipal CurrentUser user,
            @RequestParam Long branchId, @RequestParam Long customerId) {
        return ApiResponse.ok(service.invoiceableLines(user, branchId, customerId));
    }


    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('sales-invoice:view')")
    public ApiResponse<SalesInvoiceResponse> get(@AuthenticationPrincipal CurrentUser user, @PathVariable Long id) {
        return ApiResponse.ok(service.get(user, id));
    }

    @Operation(summary = "Lập", description = "Bắt buộc Idempotency-Key. Vượt số chưa xuất hóa đơn: 422 SOURCE_QUANTITY_EXCEEDED.")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('sales-invoice:create')")
    public ApiResponse<SalesInvoiceResponse> create(@AuthenticationPrincipal CurrentUser user,
            @RequestHeader(IdempotentCommand.HEADER) String idempotencyKey, @Valid @RequestBody SalesInvoiceRequest request) {
        return ApiResponse.ok(service.create(user, idempotencyKey, request));
    }

    @Operation(summary = "Sửa (DRAFT, REJECTED)", description = "Gửi version; đã đổi: 409 CONCURRENT_MODIFICATION.")
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('sales-invoice:create')")
    public ApiResponse<SalesInvoiceResponse> update(@AuthenticationPrincipal CurrentUser user, @PathVariable Long id,
            @Valid @RequestBody SalesInvoiceRequest request) {
        return ApiResponse.ok(service.update(user, id, request));
    }

    @Operation(summary = "Gửi duyệt", description = "Duyệt khi có luồng; không có luồng thì tự duyệt.")
    @PostMapping("/{id}/submit")
    @PreAuthorize("hasAuthority('sales-invoice:create')")
    public ApiResponse<SalesInvoiceResponse> submit(@AuthenticationPrincipal CurrentUser user,
            @PathVariable Long id, @RequestHeader(IdempotentCommand.HEADER) String idempotencyKey) {
        return ApiResponse.ok(service.submit(user, id, idempotencyKey));
    }

    @PostMapping("/{id}/approve")
    @PreAuthorize("hasAuthority('sales-invoice:approve')")
    public ApiResponse<SalesInvoiceResponse> approve(@AuthenticationPrincipal CurrentUser user,
            @PathVariable Long id, @RequestHeader(IdempotentCommand.HEADER) String idempotencyKey,
            @Valid @RequestBody(required = false) CommentRequest request) {
        return ApiResponse.ok(service.approve(user, id, idempotencyKey, request));
    }

    @PostMapping("/{id}/reject")
    @PreAuthorize("hasAuthority('sales-invoice:approve')")
    public ApiResponse<SalesInvoiceResponse> reject(@AuthenticationPrincipal CurrentUser user,
            @PathVariable Long id, @RequestHeader(IdempotentCommand.HEADER) String idempotencyKey,
            @Valid @RequestBody ReasonRequest request) {
        return ApiResponse.ok(service.reject(user, id, idempotencyKey, request));
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAuthority('sales-invoice:create')")
    public ApiResponse<SalesInvoiceResponse> cancel(@AuthenticationPrincipal CurrentUser user,
            @PathVariable Long id, @RequestHeader(IdempotentCommand.HEADER) String idempotencyKey,
            @Valid @RequestBody ReasonRequest request) {
        return ApiResponse.ok(service.cancel(user, id, idempotencyKey, request));
    }

    @Operation(summary = "Ghi số hóa đơn điện tử (MISA)", description = "Trùng ký hiệu + số: 409 DUPLICATE_DOCUMENT.")
    @PostMapping("/{id}/einvoice-reference")
    @PreAuthorize("hasAuthority('sales-invoice:create')")
    public ApiResponse<SalesInvoiceResponse> recordEinvoice(@AuthenticationPrincipal CurrentUser user,
            @PathVariable Long id, @RequestHeader(IdempotentCommand.HEADER) String idempotencyKey,
            @Valid @RequestBody EinvoiceReferenceRequest request) {
        return ApiResponse.ok(service.recordEinvoice(user, id, idempotencyKey, request));
    }

    @Operation(summary = "Ghi sổ", description = "Công nợ phải thu, bút toán doanh thu / thuế theo quy tắc HDB; kỳ khóa: 422 PERIOD_LOCKED; thiếu quy tắc: 422 POSTING_RULE_NOT_FOUND.")
    @PostMapping("/{id}/post")
    @PreAuthorize("hasAuthority('sales-invoice:post')")
    public ApiResponse<SalesInvoiceResponse> post(@AuthenticationPrincipal CurrentUser user,
            @PathVariable Long id, @RequestHeader(IdempotentCommand.HEADER) String idempotencyKey) {
        return ApiResponse.ok(postingService.post(user, id, idempotencyKey));
    }

    @Operation(summary = "Đảo", description = "Đã thu / bù trừ hoặc đã có trả hàng / giảm giá: 409 HAS_DEPENDENT_DOCUMENTS.")
    @PostMapping("/{id}/reverse")
    @PreAuthorize("hasAuthority('sales-invoice:reverse')")
    public ApiResponse<SalesInvoiceResponse> reverse(@AuthenticationPrincipal CurrentUser user,
            @PathVariable Long id, @RequestHeader(IdempotentCommand.HEADER) String idempotencyKey,
            @Valid @RequestBody ReverseRequest request) {
        return ApiResponse.ok(postingService.reverse(user, id, idempotencyKey, request));
    }
}
