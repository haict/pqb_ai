package com.company.erp.masterdata.api;

import com.company.erp.common.api.ApiResponse;
import com.company.erp.common.api.PageResponse;
import com.company.erp.common.application.IdempotentCommand;
import com.company.erp.common.domain.RecordStatus;
import com.company.erp.masterdata.application.MasterDataImportService;
import com.company.erp.masterdata.application.ProductService;
import com.company.erp.masterdata.application.dto.ImportResultResponse;
import com.company.erp.masterdata.application.dto.ProductRequest;
import com.company.erp.masterdata.application.dto.ProductResponse;
import com.company.erp.masterdata.application.dto.ProductUnitRequest;
import com.company.erp.masterdata.application.dto.StockLevelsRequest;
import com.company.erp.masterdata.application.dto.UpdateProductRequest;
import com.company.erp.masterdata.domain.ProductType;
import com.company.erp.system.application.CurrentUser;
import com.company.erp.system.application.dto.ChangeStatusRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.function.Function;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
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

/** Hàng hóa (BR-MD-01..09), đơn vị quy đổi (BR-MD-20, BR-MD-21), định mức tồn, nhập Excel (BR-UX-05). */
@Tag(name = "Hàng hóa")
@RestController
@RequestMapping("/api/v1/products")
public class ProductController {

    static final MediaType XLSX =
            MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

    private final ProductService productService;
    private final MasterDataImportService importService;

    public ProductController(ProductService productService, MasterDataImportService importService) {
        this.productService = productService;
        this.importService = importService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('product:view')")
    public ApiResponse<PageResponse<ProductResponse>> search(@AuthenticationPrincipal CurrentUser user,
                                                             @RequestParam Long companyId,
                                                             @RequestParam(required = false) String keyword,
                                                             @RequestParam(required = false) Long categoryId,
                                                             @RequestParam(required = false) ProductType productType,
                                                             @RequestParam(required = false) RecordStatus status,
                                                             Pageable pageable) {
        return ApiResponse.ok(PageResponse.from(productService.search(user, companyId, keyword, categoryId,
                productType, status, pageable), Function.identity()));
    }

    @Operation(summary = "Chi tiết hàng hóa kèm đơn vị quy đổi, định mức tồn")
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('product:view')")
    public ApiResponse<ProductResponse> get(@AuthenticationPrincipal CurrentUser user, @PathVariable Long id) {
        return ApiResponse.ok(productService.get(user, id));
    }

    @Operation(summary = "Thêm hàng hóa", description = "Bắt buộc header Idempotency-Key.")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('product:manage')")
    public ApiResponse<ProductResponse> create(@AuthenticationPrincipal CurrentUser user,
                                               @RequestHeader(IdempotentCommand.HEADER) String idempotencyKey,
                                               @Valid @RequestBody ProductRequest request) {
        return ApiResponse.ok(productService.create(user, idempotencyKey, request));
    }

    @Operation(summary = "Sửa hàng hóa",
            description = "Đã phát sinh giao dịch kho thì không đổi loại hàng, ĐVT cơ sở, cờ lô/hạn/serial (409 DATA_IN_USE).")
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('product:manage')")
    public ApiResponse<ProductResponse> update(@AuthenticationPrincipal CurrentUser user, @PathVariable Long id,
                                               @Valid @RequestBody UpdateProductRequest request) {
        return ApiResponse.ok(productService.update(user, id, request));
    }

    @Operation(summary = "Ngừng kinh doanh / kích hoạt lại (BR-MD-12)")
    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAuthority('product:manage')")
    public ApiResponse<ProductResponse> changeStatus(@AuthenticationPrincipal CurrentUser user, @PathVariable Long id,
                                                     @Valid @RequestBody ChangeStatusRequest request) {
        return ApiResponse.ok(productService.changeStatus(user, id, request));
    }

    @Operation(summary = "Thêm đơn vị giao dịch / hệ số quy đổi mới có hiệu lực",
            description = "Hệ số đã tạo không sửa (BR-MD-21).")
    @PostMapping("/{id}/units")
    @PreAuthorize("hasAuthority('product:manage')")
    public ApiResponse<ProductResponse> addUnit(@AuthenticationPrincipal CurrentUser user, @PathVariable Long id,
                                                @Valid @RequestBody ProductUnitRequest request) {
        return ApiResponse.ok(productService.addUnit(user, id, request));
    }

    @PatchMapping("/{id}/units/{unitRowId}/status")
    @PreAuthorize("hasAuthority('product:manage')")
    public ApiResponse<ProductResponse> changeUnitStatus(@AuthenticationPrincipal CurrentUser user,
                                                         @PathVariable Long id, @PathVariable Long unitRowId,
                                                         @Valid @RequestBody ChangeStatusRequest request) {
        return ApiResponse.ok(productService.changeUnitStatus(user, id, unitRowId, request));
    }

    @Operation(summary = "Thay định mức tồn theo kho (BR-MD-09)")
    @PutMapping("/{id}/stock-levels")
    @PreAuthorize("hasAuthority('warehouse:manage')")
    public ApiResponse<ProductResponse> replaceStockLevels(@AuthenticationPrincipal CurrentUser user,
                                                           @PathVariable Long id,
                                                           @Valid @RequestBody StockLevelsRequest request) {
        return ApiResponse.ok(productService.replaceStockLevels(user, id, request));
    }

    @Operation(summary = "Tải file mẫu nhập hàng hóa (.xlsx)")
    @GetMapping("/import-template")
    @PreAuthorize("hasAuthority('product:manage')")
    public ResponseEntity<byte[]> importTemplate() {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"mau-nhap-hang-hoa.xlsx\"")
                .contentType(XLSX)
                .body(importService.productTemplate());
    }

    @Operation(summary = "Nhập hàng hóa từ Excel",
            description = "Tất cả hoặc không: có lỗi thì trả 400 kèm lỗi theo dòng (rows[n].cột). Bắt buộc Idempotency-Key.")
    @PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('product:manage')")
    public ApiResponse<ImportResultResponse> importProducts(@AuthenticationPrincipal CurrentUser user,
                                                            @RequestHeader(IdempotentCommand.HEADER) String idempotencyKey,
                                                            @RequestParam Long companyId,
                                                            @RequestPart("file") MultipartFile file) {
        return ApiResponse.ok(importService.importProducts(user, companyId, idempotencyKey, file));
    }
}
