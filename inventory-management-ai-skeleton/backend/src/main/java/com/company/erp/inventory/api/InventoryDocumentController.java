package com.company.erp.inventory.api;

import com.company.erp.common.api.ApiResponse;
import com.company.erp.common.api.PageResponse;
import com.company.erp.common.application.IdempotentCommand;
import com.company.erp.inventory.application.InventoryDocumentPostingService;
import com.company.erp.inventory.application.InventoryDocumentService;
import com.company.erp.inventory.application.OpeningStockImportService;
import com.company.erp.inventory.application.dto.InventoryDocumentRequest;
import com.company.erp.inventory.application.dto.InventoryDocumentResponse;
import com.company.erp.inventory.application.dto.InventoryDocumentSummary;
import com.company.erp.inventory.application.dto.OpeningStockImportResponse;
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
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
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
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

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

    private static final MediaType XLSX =
            MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

    private final InventoryDocumentService documentService;
    private final InventoryDocumentPostingService postingService;
    private final OpeningStockImportService openingImportService;

    public InventoryDocumentController(InventoryDocumentService documentService,
                                       InventoryDocumentPostingService postingService,
                                       OpeningStockImportService openingImportService) {
        this.documentService = documentService;
        this.postingService = postingService;
        this.openingImportService = openingImportService;
    }

    @Operation(summary = "Tải file mẫu nhập tồn kho đầu kỳ (.xlsx)")
    @GetMapping("/opening-import-template")
    @PreAuthorize("hasAuthority('inventory-receipt:create')")
    public ResponseEntity<byte[]> openingImportTemplate() {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"mau-ton-kho-dau-ky.xlsx\"")
                .contentType(XLSX)
                .body(openingImportService.template());
    }

    @Operation(summary = "Nhập tồn kho đầu kỳ từ Excel",
            description = "Mỗi kho trong file lập một phiếu nhập kho đầu kỳ (PNK, OPENING) ở trạng thái nháp. Tất cả "
                    + "hoặc không: có lỗi thì trả 400 kèm lỗi theo dòng (rows[n].cột). Bắt buộc Idempotency-Key.")
    @PostMapping(value = "/opening-import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('inventory-receipt:create')")
    public ApiResponse<OpeningStockImportResponse> importOpeningStock(@AuthenticationPrincipal CurrentUser user,
            @RequestHeader(IdempotentCommand.HEADER) String idempotencyKey, @RequestParam Long companyId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate documentDate,
            @RequestPart("file") MultipartFile file) {
        return ApiResponse.ok(openingImportService.importFile(user, companyId, documentDate, idempotencyKey, file));
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

    @Operation(summary = "Chi tiết", description = "Gồm dòng, sổ nhật ký kho đã ghi, lịch sử duyệt (chứng từ trước D-61), "
            + "hành động được phép.")
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

    @Operation(summary = "Sửa", description = "Chỉ ở DRAFT; gửi version đã đọc (409 khi đã đổi).")
    @PutMapping("/{id}")
    public ApiResponse<InventoryDocumentResponse> update(@AuthenticationPrincipal CurrentUser user,
            @PathVariable Long id, @Valid @RequestBody InventoryDocumentRequest request) {
        return ApiResponse.ok(documentService.update(user, id, request));
    }

    @PostMapping("/{id}/cancel")
    public ApiResponse<InventoryDocumentResponse> cancel(@AuthenticationPrincipal CurrentUser user,
            @PathVariable Long id, @RequestHeader(IdempotentCommand.HEADER) String idempotencyKey,
            @Valid @RequestBody ReasonRequest request) {
        return ApiResponse.ok(documentService.cancel(user, id, idempotencyKey, request));
    }

    @Operation(summary = "Ghi sổ", description = "Từ DRAFT (hoặc APPROVED trước D-61). Kiểm tra kỳ, kiểm kê, tồn "
            + "khả dụng tại thời điểm ghi sổ (422 "
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

    @Operation(summary = "Lập chứng từ đảo", description = "Trả chứng từ đảo (PDK) ở trạng thái nháp (D-61 không qua "
            + "duyệt); chứng từ gốc chuyển REVERSED khi PDK được ghi sổ.")
    @PostMapping("/{id}/reverse")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<InventoryDocumentResponse> reverse(@AuthenticationPrincipal CurrentUser user,
            @PathVariable Long id, @RequestHeader(IdempotentCommand.HEADER) String idempotencyKey,
            @Valid @RequestBody ReverseRequest request) {
        return ApiResponse.ok(documentService.reverse(user, id, idempotencyKey, request));
    }
}
