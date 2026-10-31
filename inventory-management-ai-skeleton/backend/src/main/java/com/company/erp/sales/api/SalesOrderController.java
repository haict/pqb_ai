package com.company.erp.sales.api;

import com.company.erp.common.api.ApiResponse;
import com.company.erp.common.api.PageResponse;
import com.company.erp.common.application.IdempotentCommand;
import com.company.erp.sales.application.SalesOrderLineImportService;
import com.company.erp.sales.application.SalesOrderPostingService;
import com.company.erp.sales.application.SalesOrderService;
import com.company.erp.sales.application.SalesOrderSlipService;
import com.company.erp.sales.application.SalesReceiptSourceService;
import com.company.erp.sales.application.dto.ConfirmDeliveryRequest;
import com.company.erp.sales.application.dto.DeletedOrderResponse;
import com.company.erp.sales.application.dto.EinvoiceReferenceRequest;
import com.company.erp.sales.application.dto.PrintCompanyResponse;
import com.company.erp.sales.application.dto.ReasonRequest;
import com.company.erp.sales.application.dto.ReceiptSourceResponse;
import com.company.erp.sales.application.dto.ReverseRequest;
import com.company.erp.sales.application.dto.SalesOrderLineImportResponse;
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
 * Đơn bán (BR-SAL-10..14, 21..23, 30, 31, D-54): chứng từ ghi sổ duy nhất của nghiệp vụ bán. Không qua duyệt (D-61):
 * thẩm quyền giá / hạn mức tín dụng kiểm tra khi ghi sổ; kiểm tra tồn khả dụng, giữ / nhả hàng; ghi sổ = xuất kho + doanh thu + công nợ phải thu + bút toán; số hóa đơn
 * điện tử; theo dõi giao hàng; đảo (kể cả giao thất bại); lịch thu tiền.
 */
@Tag(name = "Đơn bán")
@RestController
@RequestMapping("/api/v1/sales-orders")
public class SalesOrderController {

    private static final MediaType XLSX =
            MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

    private final SalesOrderService service;
    private final SalesOrderPostingService posting;
    private final SalesOrderSlipService slips;
    private final SalesOrderLineImportService lineImport;
    private final SalesReceiptSourceService receiptSources;

    public SalesOrderController(SalesOrderService service, SalesOrderPostingService posting,
                                SalesOrderSlipService slips, SalesOrderLineImportService lineImport,
                                SalesReceiptSourceService receiptSources) {
        this.service = service;
        this.posting = posting;
        this.slips = slips;
        this.lineImport = lineImport;
        this.receiptSources = receiptSources;
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

    @Operation(summary = "Chứng từ nhập còn tồn (F8)", description = "D-63: dòng nhập kho theo lô / serial còn tồn ĐẠT "
            + "của hàng tại kho của đơn (FEFO), để chọn xuất đích danh; số lượng theo đơn vị cơ sở. Hàng không quản "
            + "lý lô / serial: danh sách rỗng.")
    @GetMapping("/receipt-sources")
    @PreAuthorize("hasAuthority('sales-order:create')")
    public ApiResponse<List<ReceiptSourceResponse>> receiptSources(@AuthenticationPrincipal CurrentUser user,
            @RequestParam Long branchId, @RequestParam Long warehouseId, @RequestParam Long productId) {
        return ApiResponse.ok(receiptSources.available(user, branchId, warehouseId, productId));
    }

    @Operation(summary = "Công ty in đầu phiếu",
            description = "Công ty đang hoạt động trong phạm vi người dùng, để chọn tên / địa chỉ in trên phiếu xuất kho.")
    @GetMapping("/print-companies")
    @PreAuthorize("hasAuthority('sales-order:view')")
    public ApiResponse<List<PrintCompanyResponse>> printCompanies(@AuthenticationPrincipal CurrentUser user) {
        return ApiResponse.ok(slips.printCompanies(user));
    }

    @Operation(summary = "Tải file mẫu nhập dòng hàng đơn bán (.xlsx)")
    @GetMapping("/line-import-template")
    @PreAuthorize("hasAuthority('sales-order:create')")
    public ResponseEntity<byte[]> lineImportTemplate() {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"mau-dong-don-ban.xlsx\"")
                .contentType(XLSX)
                .body(lineImport.template());
    }

    @Operation(summary = "Đọc dòng hàng đơn bán từ Excel",
            description = "Chỉ đọc và kiểm tra file, trả dòng để điền vào form; không lưu nên không cần Idempotency-Key. "
                    + "Có lỗi thì trả 400 kèm lỗi theo dòng (rows[n].cột).")
    @PostMapping(value = "/line-import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('sales-order:create')")
    public ApiResponse<SalesOrderLineImportResponse> importLines(@AuthenticationPrincipal CurrentUser user,
            @RequestParam Long companyId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate documentDate,
            @RequestPart("file") MultipartFile file) {
        return ApiResponse.ok(lineImport.parse(user, companyId, documentDate, file));
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

    @Operation(summary = "Sửa (DRAFT)", description = "Gửi version; đã đổi: 409 CONCURRENT_MODIFICATION.")
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('sales-order:create')")
    public ApiResponse<SalesOrderResponse> update(@AuthenticationPrincipal CurrentUser user, @PathVariable Long id,
            @Valid @RequestBody SalesOrderRequest request) {
        return ApiResponse.ok(service.update(user, id, request));
    }

    @Operation(summary = "Xóa", description = "D-65: xóa hẳn đơn chưa từng ghi sổ (nháp; đơn cũ trước D-61 chưa ghi "
            + "sổ), nhả hàng đang giữ; bắt buộc lý do, audit lưu đơn trước khi xóa. Đã ghi sổ, đã đảo hoặc đã từng ghi "
            + "sổ rồi bỏ ghi sổ: 409 INVALID_DOCUMENT_STATE.")
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('sales-order:create')")
    public ApiResponse<DeletedOrderResponse> delete(@AuthenticationPrincipal CurrentUser user, @PathVariable Long id,
            @RequestHeader(IdempotentCommand.HEADER) String idempotencyKey,
            @Valid @RequestBody ReasonRequest request) {
        return ApiResponse.ok(service.delete(user, id, idempotencyKey, request));
    }

    @Operation(summary = "Giữ hàng", description = "Đơn chưa ghi sổ (nháp; đơn đã duyệt trước D-61): giữ dòng hàng hóa, "
            + "hết hạn sau sales.reservation-days (D-37).")
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

    @Operation(summary = "Ghi sổ", description = "Từ DRAFT (hoặc APPROVED trước D-61): phiếu xuất kho (FEFO / lô chỉ "
            + "định, dùng hàng đã giữ, hạn dùng tối thiểu của khách D-07), doanh thu, công nợ phải thu, bút toán. Giá "
            + "dưới sàn / chiết khấu vượt ngưỡng mà thiếu quyền sales-order:override-price: 422 PRICE_BELOW_FLOOR; "
            + "vượt hạn mức tín dụng / nợ quá hạn mà thiếu quyền sales-order:override-credit: 422 "
            + "CREDIT_LIMIT_EXCEEDED.")
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

    @Operation(summary = "Bỏ ghi sổ", description = "D-62: đảo phiếu xuất kho, công nợ, bút toán theo ngày hạch toán "
            + "gốc rồi đưa đơn về nháp (giữ số) để sửa và ghi sổ lại; bắt buộc lý do. Kỳ gốc đã khóa: 422 "
            + "PERIOD_LOCKED. Đã có hóa đơn điện tử hoặc đã chuyển đi giao: 409 INVALID_DOCUMENT_STATE (dùng đảo). Còn "
            + "trả hàng / giảm giá chưa đảo, có khiếu nại, đã thu tiền / bù trừ: 409 HAS_DEPENDENT_DOCUMENTS.")
    @PostMapping("/{id}/unpost")
    @PreAuthorize("hasAuthority('sales-order:unpost')")
    public ApiResponse<SalesOrderResponse> unpost(@AuthenticationPrincipal CurrentUser user, @PathVariable Long id,
            @RequestHeader(IdempotentCommand.HEADER) String idempotencyKey,
            @Valid @RequestBody ReasonRequest request) {
        return ApiResponse.ok(posting.unpost(user, id, idempotencyKey, request));
    }
}
