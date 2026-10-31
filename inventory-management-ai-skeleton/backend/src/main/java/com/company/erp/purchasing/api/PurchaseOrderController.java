package com.company.erp.purchasing.api;

import com.company.erp.common.api.ApiResponse;
import com.company.erp.common.api.PageResponse;
import com.company.erp.common.application.IdempotentCommand;
import com.company.erp.purchasing.application.PurchaseOrderLineImportService;
import com.company.erp.purchasing.application.PurchaseOrderPostingService;
import com.company.erp.purchasing.application.PurchaseOrderService;
import com.company.erp.purchasing.application.PurchaseOrderSlipService;
import com.company.erp.purchasing.application.dto.DeletedOrderResponse;
import com.company.erp.purchasing.application.dto.PrintCompanyResponse;
import com.company.erp.purchasing.application.dto.PurchaseOrderLineImportResponse;
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
import java.util.List;
import java.util.function.Function;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
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
 * Đơn mua hàng (DMH, BR-PUR-04, D-54): chứng từ ghi sổ duy nhất của nghiệp vụ mua, mang hóa đơn của nhà cung cấp; không
 * qua duyệt (D-61): ghi sổ nhập kho + công nợ phải trả + bút toán thẳng từ nháp, đảo.
 */
@Tag(name = "Đơn mua hàng")
@RestController
@RequestMapping("/api/v1/purchase-orders")
public class PurchaseOrderController {

    private static final MediaType XLSX =
            MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

    private final PurchaseOrderService service;
    private final PurchaseOrderPostingService posting;
    private final PurchaseOrderSlipService slips;
    private final PurchaseOrderLineImportService lineImport;

    public PurchaseOrderController(PurchaseOrderService service, PurchaseOrderPostingService posting,
                                   PurchaseOrderSlipService slips, PurchaseOrderLineImportService lineImport) {
        this.service = service;
        this.posting = posting;
        this.slips = slips;
        this.lineImport = lineImport;
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
                                                        @PathVariable Long id,
                                                        @RequestParam(required = false) Long companyId) {
        return ApiResponse.ok(slips.stockInSlip(user, id, companyId));
    }

    @Operation(summary = "Công ty in đầu phiếu",
            description = "Công ty đang hoạt động trong phạm vi người dùng, để chọn tên / địa chỉ in trên phiếu nhập kho.")
    @GetMapping("/print-companies")
    @PreAuthorize("hasAuthority('purchase-order:view')")
    public ApiResponse<List<PrintCompanyResponse>> printCompanies(@AuthenticationPrincipal CurrentUser user) {
        return ApiResponse.ok(slips.printCompanies(user));
    }

    @Operation(summary = "Tải file mẫu nhập dòng hàng đơn mua (.xlsx)")
    @GetMapping("/line-import-template")
    @PreAuthorize("hasAuthority('purchase-order:create')")
    public ResponseEntity<byte[]> lineImportTemplate() {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"mau-dong-don-mua.xlsx\"")
                .contentType(XLSX)
                .body(lineImport.template());
    }

    @Operation(summary = "Đọc dòng hàng đơn mua từ Excel",
            description = "Chỉ đọc và kiểm tra file, trả dòng để điền vào form; không lưu nên không cần Idempotency-Key. "
                    + "Có lỗi thì trả 400 kèm lỗi theo dòng (rows[n].cột).")
    @PostMapping(value = "/line-import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('purchase-order:create')")
    public ApiResponse<PurchaseOrderLineImportResponse> importLines(@AuthenticationPrincipal CurrentUser user,
            @RequestParam Long companyId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate documentDate,
            @RequestPart("file") MultipartFile file) {
        return ApiResponse.ok(lineImport.parse(user, companyId, documentDate, file));
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

    @Operation(summary = "Sửa (DRAFT)", description = "Gửi version; đã đổi: 409 CONCURRENT_MODIFICATION.")
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('purchase-order:create')")
    public ApiResponse<PurchaseOrderResponse> update(@AuthenticationPrincipal CurrentUser user, @PathVariable Long id,
            @Valid @RequestBody PurchaseOrderRequest request) {
        return ApiResponse.ok(service.update(user, id, request));
    }

    @Operation(summary = "Xóa", description = "D-65: xóa hẳn đơn chưa từng ghi sổ (nháp; đơn cũ trước D-61 chưa ghi "
            + "sổ), đơn đã duyệt trước D-61 thì trừ hàng đang về; bắt buộc lý do, audit lưu đơn trước khi xóa. Đã ghi "
            + "sổ, đã đảo hoặc đã từng ghi sổ rồi bỏ ghi sổ: 409 INVALID_DOCUMENT_STATE.")
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('purchase-order:create')")
    public ApiResponse<DeletedOrderResponse> delete(@AuthenticationPrincipal CurrentUser user, @PathVariable Long id,
            @RequestHeader(IdempotentCommand.HEADER) String idempotencyKey,
            @Valid @RequestBody ReasonRequest request) {
        return ApiResponse.ok(service.delete(user, id, idempotencyKey, request));
    }

    @Operation(summary = "Ghi sổ", description = "Từ DRAFT (hoặc APPROVED trước D-61): phiếu nhập kho (lô, hạn dùng, "
            + "serial), công nợ phải trả, bút toán. Tổng tiền từ ngưỡng purchasing.order-approval-threshold mà thiếu "
            + "quyền purchase-order:post-over-threshold: 422 AUTHORITY_LIMIT_EXCEEDED. Hóa đơn trùng: 409 "
            + "DUPLICATE_DOCUMENT.")
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

    @Operation(summary = "Bỏ ghi sổ", description = "D-62: đảo phiếu nhập kho, công nợ, bút toán theo ngày hạch toán "
            + "gốc rồi đưa đơn về nháp (giữ số) để sửa và ghi sổ lại; bắt buộc lý do. Kỳ gốc đã khóa: 422 "
            + "PERIOD_LOCKED. Còn trả hàng / giảm giá / phân bổ chưa đảo, hàng đã xuất hoặc đã thanh toán: 409 "
            + "HAS_DEPENDENT_DOCUMENTS.")
    @PostMapping("/{id}/unpost")
    @PreAuthorize("hasAuthority('purchase-order:unpost')")
    public ApiResponse<PurchaseOrderResponse> unpost(@AuthenticationPrincipal CurrentUser user, @PathVariable Long id,
            @RequestHeader(IdempotentCommand.HEADER) String idempotencyKey,
            @Valid @RequestBody ReasonRequest request) {
        return ApiResponse.ok(posting.unpost(user, id, idempotencyKey, request));
    }
}
