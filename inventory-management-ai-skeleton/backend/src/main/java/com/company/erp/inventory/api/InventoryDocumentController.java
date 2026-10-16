package com.company.erp.inventory.api;

import com.company.erp.common.api.ApiResponse;
import com.company.erp.common.api.PageResponse;
import com.company.erp.common.application.IdempotentCommand;
import com.company.erp.inventory.application.InventoryDocumentPostingService;
import com.company.erp.inventory.application.InventoryDocumentService;
import com.company.erp.inventory.application.dto.CommentRequest;
import com.company.erp.inventory.application.dto.InventoryDocumentRequest;
import com.company.erp.inventory.application.dto.InventoryDocumentResponse;
import com.company.erp.inventory.application.dto.InventoryDocumentSummary;
import com.company.erp.inventory.application.dto.ReasonRequest;
import com.company.erp.inventory.application.dto.ReceiveRequest;
import com.company.erp.inventory.application.dto.ReverseRequest;
import com.company.erp.inventory.domain.InventoryDocumentStatus;
import com.company.erp.inventory.domain.InventoryDocumentType;
import com.company.erp.system.application.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.function.Function;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
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
 * Chứng từ kho: nhập (PNK), xuất nội bộ (PXK), xuất hủy (PXH), chuyển kho (PCK), điều chỉnh (PDC), chuyển trạng thái
 * chất lượng (PCL), đảo (PDK). Quyền theo loại chứng từ ({@code <loại>:view|create|approve|post}) kiểm tra ở service;
 * chứng từ ngoài phạm vi kho trả 404. Lệnh tạo và hành động bắt buộc header Idempotency-Key.
 */
@Tag(name = "Chứng từ kho")
@RestController
@RequestMapping("/api/v1/inventory-documents")
@PreAuthorize("hasAnyAuthority('inventory:view', 'inventory-receipt:view', 'inventory-issue:view', "
        + "'inventory-transfer:view', 'inventory-adjustment:view')")
public class InventoryDocumentController {

    private final InventoryDocumentService documentService;
    private final InventoryDocumentPostingService postingService;

    public InventoryDocumentController(InventoryDocumentService documentService,
                                       InventoryDocumentPostingService postingService) {
        this.documentService = documentService;
        this.postingService = postingService;
    }

    @GetMapping
    public ApiResponse<PageResponse<InventoryDocumentSummary>> search(@AuthenticationPrincipal CurrentUser user,
            @RequestParam Long companyId,
            @RequestParam(required = false) InventoryDocumentType documentType,
            @RequestParam(required = false) String subType,
            @RequestParam(required = false) InventoryDocumentStatus status,
            @RequestParam(required = false) Long warehouseId,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo,
            Pageable pageable) {
        InventoryDocumentService.SearchFilter filter = new InventoryDocumentService.SearchFilter(companyId,
                documentType, subType, status, warehouseId, keyword, dateFrom, dateTo);
        return ApiResponse.ok(PageResponse.from(documentService.search(user, filter, pageable), Function.identity()));
    }

    @Operation(summary = "Chi tiết", description = "Gồm dòng, sổ nhật ký kho đã ghi, lịch sử duyệt, hành động được phép.")
    @GetMapping("/{id}")
    public ApiResponse<InventoryDocumentResponse> get(@AuthenticationPrincipal CurrentUser user,
                                                      @PathVariable Long id) {
        return ApiResponse.ok(documentService.get(user, id));
    }

    @Operation(summary = "Lập chứng từ (nháp)", description = "Quyền <loại>:create. Không lập trực tiếp chứng từ đảo.")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<InventoryDocumentResponse> create(@AuthenticationPrincipal CurrentUser user,
            @RequestHeader(IdempotentCommand.HEADER) String idempotencyKey,
            @Valid @RequestBody InventoryDocumentRequest request) {
        return ApiResponse.ok(documentService.create(user, idempotencyKey, request));
    }

    @Operation(summary = "Sửa", description = "Chỉ ở DRAFT/REJECTED; gửi version đã đọc (409 khi đã đổi).")
    @PutMapping("/{id}")
    public ApiResponse<InventoryDocumentResponse> update(@AuthenticationPrincipal CurrentUser user,
            @PathVariable Long id, @Valid @RequestBody InventoryDocumentRequest request) {
        return ApiResponse.ok(documentService.update(user, id, request));
    }

    @Operation(summary = "Gửi duyệt", description = "Loại không cần duyệt hoặc không có luồng khớp: chuyển APPROVED.")
    @PostMapping("/{id}/submit")
    public ApiResponse<InventoryDocumentResponse> submit(@AuthenticationPrincipal CurrentUser user,
            @PathVariable Long id, @RequestHeader(IdempotentCommand.HEADER) String idempotencyKey) {
        return ApiResponse.ok(documentService.submit(user, id, idempotencyKey));
    }

    @PostMapping("/{id}/approve")
    public ApiResponse<InventoryDocumentResponse> approve(@AuthenticationPrincipal CurrentUser user,
            @PathVariable Long id, @RequestHeader(IdempotentCommand.HEADER) String idempotencyKey,
            @Valid @RequestBody(required = false) CommentRequest request) {
        return ApiResponse.ok(documentService.approve(user, id, idempotencyKey, request));
    }

    @PostMapping("/{id}/reject")
    public ApiResponse<InventoryDocumentResponse> reject(@AuthenticationPrincipal CurrentUser user,
            @PathVariable Long id, @RequestHeader(IdempotentCommand.HEADER) String idempotencyKey,
            @Valid @RequestBody ReasonRequest request) {
        return ApiResponse.ok(documentService.reject(user, id, idempotencyKey, request));
    }

    @PostMapping("/{id}/cancel")
    public ApiResponse<InventoryDocumentResponse> cancel(@AuthenticationPrincipal CurrentUser user,
            @PathVariable Long id, @RequestHeader(IdempotentCommand.HEADER) String idempotencyKey,
            @Valid @RequestBody ReasonRequest request) {
        return ApiResponse.ok(documentService.cancel(user, id, idempotencyKey, request));
    }

    @Operation(summary = "Ghi sổ", description = "Kiểm tra kỳ, kiểm kê, tồn khả dụng tại thời điểm ghi sổ (422 "
            + "INSUFFICIENT_STOCK, PERIOD_LOCKED, LOT_EXPIRED, STOCK_COUNT_IN_PROGRESS...).")
    @PostMapping("/{id}/post")
    public ApiResponse<InventoryDocumentResponse> post(@AuthenticationPrincipal CurrentUser user,
            @PathVariable Long id, @RequestHeader(IdempotentCommand.HEADER) String idempotencyKey) {
        return ApiResponse.ok(postingService.post(user, id, idempotencyKey));
    }

    @Operation(summary = "Nhận hàng chuyển kho khác chi nhánh", description = "Người thuộc kho đích xác nhận.")
    @PostMapping("/{id}/receive")
    public ApiResponse<InventoryDocumentResponse> receive(@AuthenticationPrincipal CurrentUser user,
            @PathVariable Long id, @RequestHeader(IdempotentCommand.HEADER) String idempotencyKey,
            @Valid @RequestBody(required = false) ReceiveRequest request) {
        return ApiResponse.ok(postingService.receive(user, id, idempotencyKey, request));
    }

    @Operation(summary = "Lập chứng từ đảo", description = "Trả chứng từ đảo (PDK) đã gửi duyệt; chứng từ gốc chuyển "
            + "REVERSED khi PDK được ghi sổ.")
    @PostMapping("/{id}/reverse")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<InventoryDocumentResponse> reverse(@AuthenticationPrincipal CurrentUser user,
            @PathVariable Long id, @RequestHeader(IdempotentCommand.HEADER) String idempotencyKey,
            @Valid @RequestBody ReverseRequest request) {
        return ApiResponse.ok(documentService.reverse(user, id, idempotencyKey, request));
    }
}
