package com.company.erp.sales.api;

import com.company.erp.common.api.ApiResponse;
import com.company.erp.common.api.PageResponse;
import com.company.erp.common.application.IdempotentCommand;
import com.company.erp.sales.application.SalesReturnPostingService;
import com.company.erp.sales.application.SalesReturnService;
import com.company.erp.sales.application.dto.CommentRequest;
import com.company.erp.sales.application.dto.ReasonRequest;
import com.company.erp.sales.application.dto.ReturnableSalesLineResponse;
import com.company.erp.sales.application.dto.ReverseRequest;
import com.company.erp.sales.application.dto.SalesDeliveryResponse;
import com.company.erp.sales.application.dto.SalesReturnRequest;
import com.company.erp.sales.application.dto.SalesReturnResponse;
import com.company.erp.sales.domain.SalesDocumentStatus;
import com.company.erp.sales.domain.SalesReturnType;
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
 * Hàng bán bị trả lại (TLB) và giảm giá hàng bán (GGB), BR-SAL-32, 33, 36: đúng lô / serial đã xuất, nhập lại kho cách
 * ly, giảm doanh thu và công nợ phải thu; đổi hàng (phiếu giao mới liên kết); đảo.
 */
@Tag(name = "Trả hàng / giảm giá hàng bán")
@RestController
@RequestMapping("/api/v1/sales-returns")
public class SalesReturnController {

    private final SalesReturnService service;
    private final SalesReturnPostingService postingService;

    public SalesReturnController(SalesReturnService service, SalesReturnPostingService postingService) {
        this.service = service;
        this.postingService = postingService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('sales-return:view')")
    public ApiResponse<PageResponse<SalesReturnResponse>> search(@AuthenticationPrincipal CurrentUser user,
            @RequestParam Long companyId, @RequestParam(required = false) Long branchId,
            @RequestParam(required = false) SalesReturnType documentType,
            @RequestParam(required = false) Long customerId,
            @RequestParam(required = false) SalesDocumentStatus status,
            @RequestParam(required = false) LocalDate dateFrom, @RequestParam(required = false) LocalDate dateTo,
            @RequestParam(required = false) String keyword, Pageable pageable) {
        return ApiResponse.ok(PageResponse.from(service.search(user, new SalesReturnService.ReturnFilter(companyId, branchId, documentType, status, customerId, dateFrom, dateTo,
                keyword), pageable),
                Function.identity()));
    }

    @Operation(summary = "Dòng còn trả / giảm giá được", description = "invoiceId trống: theo (dòng phiếu giao, lô) chưa xuất hóa đơn; có invoiceId: theo dòng hóa đơn.")
    @GetMapping("/returnable-lines")
    @PreAuthorize("hasAuthority('sales-return:view')")
    public ApiResponse<List<ReturnableSalesLineResponse>> returnableLines(@AuthenticationPrincipal CurrentUser user,
            @RequestParam Long branchId, @RequestParam Long customerId,
            @RequestParam(required = false) Long invoiceId) {
        return ApiResponse.ok(service.returnableLines(user, branchId, customerId, invoiceId));
    }


    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('sales-return:view')")
    public ApiResponse<SalesReturnResponse> get(@AuthenticationPrincipal CurrentUser user, @PathVariable Long id) {
        return ApiResponse.ok(service.get(user, id));
    }

    @Operation(summary = "Lập", description = "Bắt buộc Idempotency-Key. Vượt số còn trả (dòng, lô) / giá trị còn lại: 422 SOURCE_QUANTITY_EXCEEDED.")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('sales-return:create')")
    public ApiResponse<SalesReturnResponse> create(@AuthenticationPrincipal CurrentUser user,
            @RequestHeader(IdempotentCommand.HEADER) String idempotencyKey, @Valid @RequestBody SalesReturnRequest request) {
        return ApiResponse.ok(service.create(user, idempotencyKey, request));
    }

    @Operation(summary = "Sửa (DRAFT, REJECTED)", description = "Gửi version; đã đổi: 409 CONCURRENT_MODIFICATION.")
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('sales-return:create')")
    public ApiResponse<SalesReturnResponse> update(@AuthenticationPrincipal CurrentUser user, @PathVariable Long id,
            @Valid @RequestBody SalesReturnRequest request) {
        return ApiResponse.ok(service.update(user, id, request));
    }

    @Operation(summary = "Gửi duyệt", description = "Duyệt khi có luồng; không có luồng thì tự duyệt.")
    @PostMapping("/{id}/submit")
    @PreAuthorize("hasAuthority('sales-return:create')")
    public ApiResponse<SalesReturnResponse> submit(@AuthenticationPrincipal CurrentUser user,
            @PathVariable Long id, @RequestHeader(IdempotentCommand.HEADER) String idempotencyKey) {
        return ApiResponse.ok(service.submit(user, id, idempotencyKey));
    }

    @PostMapping("/{id}/approve")
    @PreAuthorize("hasAuthority('sales-return:approve')")
    public ApiResponse<SalesReturnResponse> approve(@AuthenticationPrincipal CurrentUser user,
            @PathVariable Long id, @RequestHeader(IdempotentCommand.HEADER) String idempotencyKey,
            @Valid @RequestBody(required = false) CommentRequest request) {
        return ApiResponse.ok(service.approve(user, id, idempotencyKey, request));
    }

    @PostMapping("/{id}/reject")
    @PreAuthorize("hasAuthority('sales-return:approve')")
    public ApiResponse<SalesReturnResponse> reject(@AuthenticationPrincipal CurrentUser user,
            @PathVariable Long id, @RequestHeader(IdempotentCommand.HEADER) String idempotencyKey,
            @Valid @RequestBody ReasonRequest request) {
        return ApiResponse.ok(service.reject(user, id, idempotencyKey, request));
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAuthority('sales-return:create')")
    public ApiResponse<SalesReturnResponse> cancel(@AuthenticationPrincipal CurrentUser user,
            @PathVariable Long id, @RequestHeader(IdempotentCommand.HEADER) String idempotencyKey,
            @Valid @RequestBody ReasonRequest request) {
        return ApiResponse.ok(service.cancel(user, id, idempotencyKey, request));
    }

    @Operation(summary = "Ghi sổ", description = "Trả hàng: nhập lại kho đúng lô / serial (mặc định CÁCH LY), giảm số đã giao của đơn; có hóa đơn / giảm giá: giảm công nợ phải thu (vượt số còn nợ ghi trả trước của khách), bút toán theo quy tắc TLB / GGB.")
    @PostMapping("/{id}/post")
    @PreAuthorize("hasAuthority('sales-return:post')")
    public ApiResponse<SalesReturnResponse> post(@AuthenticationPrincipal CurrentUser user,
            @PathVariable Long id, @RequestHeader(IdempotentCommand.HEADER) String idempotencyKey) {
        return ApiResponse.ok(postingService.post(user, id, idempotencyKey));
    }

    @Operation(summary = "Đảo", description = "Hàng trả lại đã xuất tiếp / phần trả trước đã bù trừ: 409 HAS_DEPENDENT_DOCUMENTS.")
    @PostMapping("/{id}/reverse")
    @PreAuthorize("hasAuthority('sales-return:reverse')")
    public ApiResponse<SalesReturnResponse> reverse(@AuthenticationPrincipal CurrentUser user,
            @PathVariable Long id, @RequestHeader(IdempotentCommand.HEADER) String idempotencyKey,
            @Valid @RequestBody ReverseRequest request) {
        return ApiResponse.ok(postingService.reverse(user, id, idempotencyKey, request));
    }

    @Operation(summary = "Đổi hàng", description = "BR-SAL-33: lập phiếu giao nháp cho phần đã trả của cùng dòng đơn bán.")
    @PostMapping("/{id}/create-exchange-delivery")
    @PreAuthorize("hasAuthority('sales-return:create')")
    public ApiResponse<SalesDeliveryResponse> createExchangeDelivery(@AuthenticationPrincipal CurrentUser user,
            @PathVariable Long id, @RequestHeader(IdempotentCommand.HEADER) String idempotencyKey) {
        return ApiResponse.ok(service.createExchangeDelivery(user, id, idempotencyKey));
    }
}
