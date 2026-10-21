package com.company.erp.finance.api;

import com.company.erp.common.api.ApiResponse;
import com.company.erp.common.api.PageResponse;
import com.company.erp.common.application.IdempotentCommand;
import com.company.erp.finance.application.DebtOpeningLineImportService;
import com.company.erp.finance.application.DebtOpeningPostingService;
import com.company.erp.finance.application.DebtOpeningService;
import com.company.erp.finance.application.dto.DebtOpeningLineImportResponse;
import com.company.erp.finance.application.dto.DebtOpeningRequest;
import com.company.erp.finance.application.dto.DebtOpeningResponse;
import com.company.erp.finance.application.dto.ReasonRequest;
import com.company.erp.finance.application.dto.ReverseRequest;
import com.company.erp.finance.domain.DebtType;
import com.company.erp.finance.domain.FinanceDocumentStatus;
import com.company.erp.system.application.CurrentUser;
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

/** Chứng từ công nợ đầu kỳ (CNDK, BR-MIG-01): lập, ghi sổ thẳng từ nháp (không qua duyệt, D-60), hủy, đảo. */
@Tag(name = "Công nợ đầu kỳ")
@RestController
@RequestMapping("/api/v1/debt-openings")
public class DebtOpeningController {

    private static final MediaType XLSX =
            MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

    private final DebtOpeningService service;
    private final DebtOpeningPostingService postingService;
    private final DebtOpeningLineImportService lineImport;

    public DebtOpeningController(DebtOpeningService service, DebtOpeningPostingService postingService,
                                 DebtOpeningLineImportService lineImport) {
        this.service = service;
        this.postingService = postingService;
        this.lineImport = lineImport;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('debt-opening:view')")
    public ApiResponse<PageResponse<DebtOpeningResponse>> search(@AuthenticationPrincipal CurrentUser user,
            @RequestParam Long companyId, @RequestParam(required = false) Long branchId,
            @RequestParam(required = false) DebtType debtType,
            @RequestParam(required = false) FinanceDocumentStatus status,
            @RequestParam(required = false) String keyword, Pageable pageable) {
        DebtOpeningService.DebtOpeningFilter filter = new DebtOpeningService.DebtOpeningFilter(companyId, branchId,
                debtType, status, keyword);
        return ApiResponse.ok(PageResponse.from(service.search(user, filter, pageable), Function.identity()));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('debt-opening:view')")
    public ApiResponse<DebtOpeningResponse> get(@AuthenticationPrincipal CurrentUser user, @PathVariable Long id) {
        return ApiResponse.ok(service.get(user, id));
    }

    @GetMapping("/line-import-template")
    @PreAuthorize("hasAuthority('debt-opening:create')")
    public ResponseEntity<byte[]> lineImportTemplate() {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"mau-cong-no-dau-ky.xlsx\"")
                .contentType(XLSX)
                .body(lineImport.template());
    }

    @Operation(summary = "Đọc dòng công nợ đầu kỳ từ Excel",
            description = "Chỉ đọc và kiểm tra file, trả dòng để điền vào form; không lưu nên không cần Idempotency-Key. "
                    + "Có lỗi thì trả 400 kèm lỗi theo dòng (rows[n].cột).")
    @PostMapping(value = "/line-import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('debt-opening:create')")
    public ApiResponse<DebtOpeningLineImportResponse> importLines(@AuthenticationPrincipal CurrentUser user,
            @RequestParam Long companyId, @RequestParam DebtType debtType, @RequestPart("file") MultipartFile file) {
        return ApiResponse.ok(lineImport.parse(user, companyId, debtType, file));
    }

    @Operation(summary = "Lập chứng từ", description = "Bắt buộc Idempotency-Key. Hạn thanh toán trống: ngày chứng từ "
            + "+ số ngày nợ của đối tượng.")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('debt-opening:create')")
    public ApiResponse<DebtOpeningResponse> create(@AuthenticationPrincipal CurrentUser user,
                                                   @RequestHeader(IdempotentCommand.HEADER) String idempotencyKey,
                                                   @Valid @RequestBody DebtOpeningRequest request) {
        return ApiResponse.ok(service.create(user, idempotencyKey, request));
    }

    @Operation(summary = "Sửa (DRAFT)", description = "Gửi version; đã đổi: 409 CONCURRENT_MODIFICATION.")
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('debt-opening:create')")
    public ApiResponse<DebtOpeningResponse> update(@AuthenticationPrincipal CurrentUser user, @PathVariable Long id,
                                                   @Valid @RequestBody DebtOpeningRequest request) {
        return ApiResponse.ok(service.update(user, id, request));
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAuthority('debt-opening:create')")
    public ApiResponse<DebtOpeningResponse> cancel(@AuthenticationPrincipal CurrentUser user, @PathVariable Long id,
            @RequestHeader(IdempotentCommand.HEADER) String idempotencyKey, @Valid @RequestBody ReasonRequest request) {
        return ApiResponse.ok(service.cancel(user, id, idempotencyKey, request));
    }

    @Operation(summary = "Ghi sổ", description = "Từ DRAFT (hoặc APPROVED theo luồng cũ). Mỗi dòng thành một khoản "
            + "công nợ. Kỳ công nợ khóa: 422 "
            + "PERIOD_LOCKED. Đã ghi sổ: 409 INVALID_DOCUMENT_STATE.")
    @PostMapping("/{id}/post")
    @PreAuthorize("hasAuthority('debt-opening:post')")
    public ApiResponse<DebtOpeningResponse> post(@AuthenticationPrincipal CurrentUser user, @PathVariable Long id,
                                                 @RequestHeader(IdempotentCommand.HEADER) String idempotencyKey) {
        return ApiResponse.ok(postingService.post(user, id, idempotencyKey));
    }

    @Operation(summary = "Đảo", description = "Bắt buộc lý do. Khoản công nợ đã thanh toán / đối trừ: 409 "
            + "HAS_DEPENDENT_DOCUMENTS.")
    @PostMapping("/{id}/reverse")
    @PreAuthorize("hasAuthority('debt-opening:post')")
    public ApiResponse<DebtOpeningResponse> reverse(@AuthenticationPrincipal CurrentUser user, @PathVariable Long id,
            @RequestHeader(IdempotentCommand.HEADER) String idempotencyKey, @Valid @RequestBody ReverseRequest request) {
        return ApiResponse.ok(postingService.reverse(user, id, idempotencyKey, request));
    }
}
