package com.company.erp.finance.api;

import com.company.erp.common.api.ApiResponse;
import com.company.erp.common.api.PageResponse;
import com.company.erp.common.application.IdempotentCommand;
import com.company.erp.finance.application.CashDocumentPostingService;
import com.company.erp.finance.application.CashDocumentService;
import com.company.erp.finance.application.dto.CashDocumentRequest;
import com.company.erp.finance.application.dto.CashDocumentResponse;
import com.company.erp.finance.application.dto.CommentRequest;
import com.company.erp.finance.application.dto.ReasonRequest;
import com.company.erp.finance.application.dto.ReverseRequest;
import com.company.erp.finance.domain.CashDocumentType;
import com.company.erp.finance.domain.CashPurpose;
import com.company.erp.finance.domain.FinanceDocumentStatus;
import com.company.erp.system.application.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.LocalDate;
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

/** Chứng từ thu chi (BR-CSH-02, 03, BR-AR-03): phiếu thu, phiếu chi, giấy báo có, ủy nhiệm chi, chuyển tiền nội bộ. */
@Tag(name = "Thu chi, ngân hàng")
@RestController
@RequestMapping("/api/v1/cash-documents")
public class CashDocumentController {

    private final CashDocumentService service;
    private final CashDocumentPostingService postingService;

    public CashDocumentController(CashDocumentService service, CashDocumentPostingService postingService) {
        this.service = service;
        this.postingService = postingService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('cash-document:view')")
    public ApiResponse<PageResponse<CashDocumentResponse>> search(@AuthenticationPrincipal CurrentUser user,
            @RequestParam Long companyId, @RequestParam(required = false) Long branchId,
            @RequestParam(required = false) CashDocumentType documentType,
            @RequestParam(required = false) CashPurpose purpose,
            @RequestParam(required = false) FinanceDocumentStatus status,
            @RequestParam(required = false) Long cashAccountId, @RequestParam(required = false) Long partnerId,
            @RequestParam(required = false) LocalDate dateFrom, @RequestParam(required = false) LocalDate dateTo,
            @RequestParam(required = false) String keyword, Pageable pageable) {
        CashDocumentService.CashDocumentFilter filter = new CashDocumentService.CashDocumentFilter(companyId,
                branchId, documentType, purpose, status, cashAccountId, partnerId, dateFrom, dateTo, keyword);
        return ApiResponse.ok(PageResponse.from(service.search(user, filter, pageable), Function.identity()));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('cash-document:view')")
    public ApiResponse<CashDocumentResponse> get(@AuthenticationPrincipal CurrentUser user, @PathVariable Long id) {
        return ApiResponse.ok(service.get(user, id));
    }

    @Operation(summary = "Lập chứng từ", description = "Bắt buộc Idempotency-Key. Thu tiền khách / trả nhà cung cấp: "
            + "allocationMode AUTO (hóa đơn đến hạn sớm nhất trước lúc ghi sổ) hoặc MANUAL (dòng debtItemId); phần "
            + "chưa phân bổ là trả trước.")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('cash-document:create')")
    public ApiResponse<CashDocumentResponse> create(@AuthenticationPrincipal CurrentUser user,
                                                    @RequestHeader(IdempotentCommand.HEADER) String idempotencyKey,
                                                    @Valid @RequestBody CashDocumentRequest request) {
        return ApiResponse.ok(service.create(user, idempotencyKey, request));
    }

    @Operation(summary = "Sửa (DRAFT, REJECTED)", description = "Gửi version; đã đổi: 409 CONCURRENT_MODIFICATION.")
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('cash-document:create')")
    public ApiResponse<CashDocumentResponse> update(@AuthenticationPrincipal CurrentUser user, @PathVariable Long id,
                                                    @Valid @RequestBody CashDocumentRequest request) {
        return ApiResponse.ok(service.update(user, id, request));
    }

    @Operation(summary = "Gửi duyệt", description = "Chứng từ chi từ ngưỡng finance.payment-approval-threshold trở lên "
            + "chờ duyệt (chưa cấu hình luồng: 422 APPROVAL_NOT_CONFIGURED); còn lại tự duyệt.")
    @PostMapping("/{id}/submit")
    @PreAuthorize("hasAuthority('cash-document:create')")
    public ApiResponse<CashDocumentResponse> submit(@AuthenticationPrincipal CurrentUser user, @PathVariable Long id,
                                                    @RequestHeader(IdempotentCommand.HEADER) String idempotencyKey) {
        return ApiResponse.ok(service.submit(user, id, idempotencyKey));
    }

    @PostMapping("/{id}/approve")
    @PreAuthorize("hasAuthority('cash-document:approve')")
    public ApiResponse<CashDocumentResponse> approve(@AuthenticationPrincipal CurrentUser user, @PathVariable Long id,
            @RequestHeader(IdempotentCommand.HEADER) String idempotencyKey,
            @Valid @RequestBody(required = false) CommentRequest request) {
        return ApiResponse.ok(service.approve(user, id, idempotencyKey, request));
    }

    @PostMapping("/{id}/reject")
    @PreAuthorize("hasAuthority('cash-document:approve')")
    public ApiResponse<CashDocumentResponse> reject(@AuthenticationPrincipal CurrentUser user, @PathVariable Long id,
            @RequestHeader(IdempotentCommand.HEADER) String idempotencyKey, @Valid @RequestBody ReasonRequest request) {
        return ApiResponse.ok(service.reject(user, id, idempotencyKey, request));
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAuthority('cash-document:create')")
    public ApiResponse<CashDocumentResponse> cancel(@AuthenticationPrincipal CurrentUser user, @PathVariable Long id,
            @RequestHeader(IdempotentCommand.HEADER) String idempotencyKey, @Valid @RequestBody ReasonRequest request) {
        return ApiResponse.ok(service.cancel(user, id, idempotencyKey, request));
    }

    @Operation(summary = "Ghi sổ", description = "Sổ quỹ, phân bổ công nợ, bút toán trong một transaction. Quỹ tiền "
            + "mặt không đủ: 422 CASH_BALANCE_NEGATIVE; phân bổ vượt số còn lại: 422 OVER_ALLOCATION; kỳ khóa: 422 "
            + "PERIOD_LOCKED; thiếu quy tắc hạch toán: 422 POSTING_RULE_NOT_FOUND.")
    @PostMapping("/{id}/post")
    @PreAuthorize("hasAuthority('cash-document:post')")
    public ApiResponse<CashDocumentResponse> post(@AuthenticationPrincipal CurrentUser user, @PathVariable Long id,
                                                  @RequestHeader(IdempotentCommand.HEADER) String idempotencyKey) {
        return ApiResponse.ok(postingService.post(user, id, idempotencyKey));
    }

    @Operation(summary = "Đảo", description = "Bắt buộc lý do. Hoàn phân bổ vào hóa đơn, sổ quỹ ngược, bút toán đảo; "
            + "quỹ tiền mặt không đủ để đảo phiếu thu: 422 CASH_BALANCE_NEGATIVE.")
    @PostMapping("/{id}/reverse")
    @PreAuthorize("hasAuthority('cash-document:reverse')")
    public ApiResponse<CashDocumentResponse> reverse(@AuthenticationPrincipal CurrentUser user, @PathVariable Long id,
            @RequestHeader(IdempotentCommand.HEADER) String idempotencyKey, @Valid @RequestBody ReverseRequest request) {
        return ApiResponse.ok(postingService.reverse(user, id, idempotencyKey, request));
    }
}
