package com.company.erp.purchasing.api;

import com.company.erp.common.api.ApiResponse;
import com.company.erp.common.api.PageResponse;
import com.company.erp.common.application.IdempotentCommand;
import com.company.erp.purchasing.application.PurchaseReturnPostingService;
import com.company.erp.purchasing.application.PurchaseReturnService;
import com.company.erp.purchasing.application.dto.CommentRequest;
import com.company.erp.purchasing.application.dto.ReturnableLineResponse;
import com.company.erp.purchasing.application.dto.PurchaseReturnRequest;
import com.company.erp.purchasing.application.dto.PurchaseReturnResponse;
import com.company.erp.purchasing.application.dto.ReasonRequest;
import com.company.erp.purchasing.application.dto.ReverseRequest;
import com.company.erp.purchasing.domain.PurchaseDocumentStatus;
import com.company.erp.purchasing.domain.PurchaseReturnType;
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
 * Trả hàng nhà cung cấp (THN) và giảm giá hàng mua (GGM), BR-PUR-14: theo phiếu nhận hàng gốc (đúng lô, serial) hoặc
 * hóa đơn mua; ghi sổ giảm tồn, công nợ phải trả, bút toán; đảo.
 */
@Tag(name = "Trả hàng / giảm giá hàng mua")
@RestController
@RequestMapping("/api/v1/purchase-returns")
public class PurchaseReturnController {

    private final PurchaseReturnService service;
    private final PurchaseReturnPostingService postingService;

    public PurchaseReturnController(PurchaseReturnService service, PurchaseReturnPostingService postingService) {
        this.service = service;
        this.postingService = postingService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('purchase-return:view')")
    public ApiResponse<PageResponse<PurchaseReturnResponse>> search(@AuthenticationPrincipal CurrentUser user,
            @RequestParam Long companyId, @RequestParam(required = false) Long branchId,
            @RequestParam(required = false) PurchaseReturnType documentType,
            @RequestParam(required = false) Long supplierId,
            @RequestParam(required = false) PurchaseDocumentStatus status,
            @RequestParam(required = false) LocalDate dateFrom, @RequestParam(required = false) LocalDate dateTo,
            @RequestParam(required = false) String keyword, Pageable pageable) {
        return ApiResponse.ok(PageResponse.from(service.search(user, new PurchaseReturnService.ReturnFilter(
                companyId, branchId, documentType, status, supplierId, dateFrom, dateTo, keyword), pageable),
                Function.identity()));
    }

    @Operation(summary = "Dòng còn trả / giảm giá được", description = "Dòng của đơn mua đã ghi sổ orderId (cùng chi "
            + "nhánh, nhà cung cấp).")
    @GetMapping("/returnable-lines")
    @PreAuthorize("hasAuthority('purchase-return:view')")
    public ApiResponse<List<ReturnableLineResponse>> returnableLines(@AuthenticationPrincipal CurrentUser user,
            @RequestParam Long branchId, @RequestParam Long supplierId,
            @RequestParam Long orderId) {
        return ApiResponse.ok(service.returnableLines(user, branchId, supplierId, orderId));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('purchase-return:view')")
    public ApiResponse<PurchaseReturnResponse> get(@AuthenticationPrincipal CurrentUser user, @PathVariable Long id) {
        return ApiResponse.ok(service.get(user, id));
    }

    @Operation(summary = "Lập", description = "Bắt buộc Idempotency-Key. Vượt số còn trả / giá trị còn lại: 422 SOURCE_QUANTITY_EXCEEDED.")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('purchase-return:create')")
    public ApiResponse<PurchaseReturnResponse> create(@AuthenticationPrincipal CurrentUser user,
            @RequestHeader(IdempotentCommand.HEADER) String idempotencyKey,
            @Valid @RequestBody PurchaseReturnRequest request) {
        return ApiResponse.ok(service.create(user, idempotencyKey, request));
    }

    @Operation(summary = "Sửa (DRAFT, REJECTED)", description = "Gửi version; đã đổi: 409 CONCURRENT_MODIFICATION.")
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('purchase-return:create')")
    public ApiResponse<PurchaseReturnResponse> update(@AuthenticationPrincipal CurrentUser user,
            @PathVariable Long id, @Valid @RequestBody PurchaseReturnRequest request) {
        return ApiResponse.ok(service.update(user, id, request));
    }

    @Operation(summary = "Gửi duyệt", description = "Duyệt theo luồng đã cấu hình; không có luồng thì tự duyệt.")
    @PostMapping("/{id}/submit")
    @PreAuthorize("hasAuthority('purchase-return:create')")
    public ApiResponse<PurchaseReturnResponse> submit(@AuthenticationPrincipal CurrentUser user,
            @PathVariable Long id, @RequestHeader(IdempotentCommand.HEADER) String idempotencyKey) {
        return ApiResponse.ok(service.submit(user, id, idempotencyKey));
    }

    @PostMapping("/{id}/approve")
    @PreAuthorize("hasAuthority('purchase-return:approve')")
    public ApiResponse<PurchaseReturnResponse> approve(@AuthenticationPrincipal CurrentUser user,
            @PathVariable Long id, @RequestHeader(IdempotentCommand.HEADER) String idempotencyKey,
            @Valid @RequestBody(required = false) CommentRequest request) {
        return ApiResponse.ok(service.approve(user, id, idempotencyKey, request));
    }

    @PostMapping("/{id}/reject")
    @PreAuthorize("hasAuthority('purchase-return:approve')")
    public ApiResponse<PurchaseReturnResponse> reject(@AuthenticationPrincipal CurrentUser user,
            @PathVariable Long id, @RequestHeader(IdempotentCommand.HEADER) String idempotencyKey,
            @Valid @RequestBody ReasonRequest request) {
        return ApiResponse.ok(service.reject(user, id, idempotencyKey, request));
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAuthority('purchase-return:create')")
    public ApiResponse<PurchaseReturnResponse> cancel(@AuthenticationPrincipal CurrentUser user,
            @PathVariable Long id, @RequestHeader(IdempotentCommand.HEADER) String idempotencyKey,
            @Valid @RequestBody ReasonRequest request) {
        return ApiResponse.ok(service.cancel(user, id, idempotencyKey, request));
    }

    @Operation(summary = "Ghi sổ", description = "Trả hàng: phiếu xuất trả PXK đúng lô / serial; hàng đã có hóa đơn thì giảm công nợ phải trả (vượt số còn nợ "
            + "ghi trả trước) và bút toán THN. Giảm giá: giảm công nợ, giá trị nhập, bút toán GGM. Tồn không đủ: 422 "
            + "INSUFFICIENT_STOCK.")
    @PostMapping("/{id}/post")
    @PreAuthorize("hasAuthority('purchase-return:post')")
    public ApiResponse<PurchaseReturnResponse> post(@AuthenticationPrincipal CurrentUser user, @PathVariable Long id,
            @RequestHeader(IdempotentCommand.HEADER) String idempotencyKey) {
        return ApiResponse.ok(postingService.post(user, id, idempotencyKey));
    }

    @Operation(summary = "Đảo", description = "Đảo phiếu xuất trả, công nợ, bút toán; phần trả trước đã bù trừ: 409 HAS_DEPENDENT_DOCUMENTS.")
    @PostMapping("/{id}/reverse")
    @PreAuthorize("hasAuthority('purchase-return:reverse')")
    public ApiResponse<PurchaseReturnResponse> reverse(@AuthenticationPrincipal CurrentUser user,
            @PathVariable Long id, @RequestHeader(IdempotentCommand.HEADER) String idempotencyKey,
            @Valid @RequestBody ReverseRequest request) {
        return ApiResponse.ok(postingService.reverse(user, id, idempotencyKey, request));
    }
}
