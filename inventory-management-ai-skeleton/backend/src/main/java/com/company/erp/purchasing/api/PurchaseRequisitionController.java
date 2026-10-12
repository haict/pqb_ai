package com.company.erp.purchasing.api;

import com.company.erp.common.api.ApiResponse;
import com.company.erp.common.api.PageResponse;
import com.company.erp.common.application.IdempotentCommand;
import com.company.erp.purchasing.application.PurchaseRequisitionService;
import com.company.erp.purchasing.application.dto.CommentRequest;
import com.company.erp.purchasing.application.dto.PurchaseRequisitionRequest;
import com.company.erp.purchasing.application.dto.PurchaseRequisitionResponse;
import com.company.erp.purchasing.application.dto.ReasonRequest;
import com.company.erp.purchasing.domain.PurchaseDocumentStatus;
import com.company.erp.system.application.CurrentUser;
import com.company.erp.purchasing.application.dto.OpenRequisitionLineResponse;
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

/** Đề nghị mua hàng (DNM, BR-PUR-01): lập tay, duyệt khi có luồng, đóng phần chưa đặt. */
@Tag(name = "Đề nghị mua hàng")
@RestController
@RequestMapping("/api/v1/purchase-requisitions")
public class PurchaseRequisitionController {

    private final PurchaseRequisitionService service;

    public PurchaseRequisitionController(PurchaseRequisitionService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('purchase-requisition:view')")
    public ApiResponse<PageResponse<PurchaseRequisitionResponse>> search(@AuthenticationPrincipal CurrentUser user,
            @RequestParam Long companyId, @RequestParam(required = false) Long branchId,
            @RequestParam(required = false) PurchaseDocumentStatus status,
            @RequestParam(required = false) LocalDate dateFrom, @RequestParam(required = false) LocalDate dateTo,
            @RequestParam(required = false) String keyword, Pageable pageable) {
        return ApiResponse.ok(PageResponse.from(service.search(user, new PurchaseRequisitionService.RequisitionFilter(companyId, branchId,
                status, dateFrom, dateTo, keyword), pageable), Function.identity()));
    }

    @Operation(summary = "Dòng đề nghị còn chưa đặt", description = "Đề nghị đã duyệt của chi nhánh, để lập đơn mua.")
    @GetMapping("/open-lines")
    @PreAuthorize("hasAuthority('purchase-requisition:view')")
    public ApiResponse<List<OpenRequisitionLineResponse>> openLines(@AuthenticationPrincipal CurrentUser user,
            @RequestParam Long branchId, @RequestParam(required = false) Long productId) {
        return ApiResponse.ok(service.openLines(user, branchId, productId));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('purchase-requisition:view')")
    public ApiResponse<PurchaseRequisitionResponse> get(@AuthenticationPrincipal CurrentUser user, @PathVariable Long id) {
        return ApiResponse.ok(service.get(user, id));
    }

    @Operation(summary = "Lập chứng từ", description = "Bắt buộc Idempotency-Key.")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('purchase-requisition:create')")
    public ApiResponse<PurchaseRequisitionResponse> create(@AuthenticationPrincipal CurrentUser user,
            @RequestHeader(IdempotentCommand.HEADER) String idempotencyKey,
            @Valid @RequestBody PurchaseRequisitionRequest request) {
        return ApiResponse.ok(service.create(user, idempotencyKey, request));
    }

    @Operation(summary = "Sửa (DRAFT, REJECTED)", description = "Gửi version; đã đổi: 409 CONCURRENT_MODIFICATION.")
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('purchase-requisition:create')")
    public ApiResponse<PurchaseRequisitionResponse> update(@AuthenticationPrincipal CurrentUser user, @PathVariable Long id,
            @Valid @RequestBody PurchaseRequisitionRequest request) {
        return ApiResponse.ok(service.update(user, id, request));
    }

    @Operation(summary = "Gửi duyệt", description = "Duyệt theo luồng đã cấu hình; không có luồng thì tự duyệt.")
    @PostMapping("/{id}/submit")
    @PreAuthorize("hasAuthority('purchase-requisition:create')")
    public ApiResponse<PurchaseRequisitionResponse> submit(@AuthenticationPrincipal CurrentUser user, @PathVariable Long id,
            @RequestHeader(IdempotentCommand.HEADER) String idempotencyKey) {
        return ApiResponse.ok(service.submit(user, id, idempotencyKey));
    }

    @PostMapping("/{id}/approve")
    @PreAuthorize("hasAuthority('purchase-requisition:approve')")
    public ApiResponse<PurchaseRequisitionResponse> approve(@AuthenticationPrincipal CurrentUser user, @PathVariable Long id,
            @RequestHeader(IdempotentCommand.HEADER) String idempotencyKey,
            @Valid @RequestBody(required = false) CommentRequest request) {
        return ApiResponse.ok(service.approve(user, id, idempotencyKey, request));
    }

    @PostMapping("/{id}/reject")
    @PreAuthorize("hasAuthority('purchase-requisition:approve')")
    public ApiResponse<PurchaseRequisitionResponse> reject(@AuthenticationPrincipal CurrentUser user, @PathVariable Long id,
            @RequestHeader(IdempotentCommand.HEADER) String idempotencyKey,
            @Valid @RequestBody ReasonRequest request) {
        return ApiResponse.ok(service.reject(user, id, idempotencyKey, request));
    }

    @Operation(summary = "Hủy", description = "Chỉ khi chưa phát sinh chứng từ đích; bắt buộc lý do.")
    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAuthority('purchase-requisition:create')")
    public ApiResponse<PurchaseRequisitionResponse> cancel(@AuthenticationPrincipal CurrentUser user, @PathVariable Long id,
            @RequestHeader(IdempotentCommand.HEADER) String idempotencyKey,
            @Valid @RequestBody ReasonRequest request) {
        return ApiResponse.ok(service.cancel(user, id, idempotencyKey, request));
    }

    @Operation(summary = "Đóng phần còn lại", description = "BR-PUR-05; bắt buộc lý do.")
    @PostMapping("/{id}/close")
    @PreAuthorize("hasAuthority('purchase-requisition:close')")
    public ApiResponse<PurchaseRequisitionResponse> close(@AuthenticationPrincipal CurrentUser user, @PathVariable Long id,
            @RequestHeader(IdempotentCommand.HEADER) String idempotencyKey,
            @Valid @RequestBody ReasonRequest request) {
        return ApiResponse.ok(service.close(user, id, idempotencyKey, request));
    }
}
