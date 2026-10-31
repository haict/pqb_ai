package com.company.erp.masterdata.api;

import com.company.erp.common.api.ApiResponse;
import com.company.erp.common.api.PageResponse;
import com.company.erp.common.application.IdempotentCommand;
import com.company.erp.common.domain.RecordStatus;
import com.company.erp.system.application.CurrentUser;
import com.company.erp.system.application.dto.ChangeStatusRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.function.Function;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
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
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import com.company.erp.masterdata.application.PartnerService;
import com.company.erp.masterdata.application.TaxCodeLookupService;
import com.company.erp.masterdata.application.dto.PartnerRequest;
import com.company.erp.masterdata.application.dto.PartnerResponse;
import com.company.erp.masterdata.application.dto.TaxCodeLookupResponse;
import com.company.erp.masterdata.application.dto.UpdatePartnerRequest;
import com.company.erp.masterdata.domain.PartnerRole;

/** Đối tác (BR-MD-30..35). Người dùng "chỉ dữ liệu của mình" chỉ thấy đối tác mình phụ trách. */
@Tag(name = "Đối tác")
@RestController
@RequestMapping("/api/v1/partners")
public class PartnerController {

    private final PartnerService partnerService;
    private final TaxCodeLookupService taxCodeLookupService;

    public PartnerController(PartnerService partnerService, TaxCodeLookupService taxCodeLookupService) {
        this.partnerService = partnerService;
        this.taxCodeLookupService = taxCodeLookupService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('partner:view')")
    public ApiResponse<PageResponse<PartnerResponse>> search(@AuthenticationPrincipal CurrentUser user,
            @RequestParam Long companyId,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) PartnerRole role,
            @RequestParam(required = false) RecordStatus status,
            Pageable pageable) {
        return ApiResponse.ok(PageResponse.from(partnerService.search(user, companyId, keyword, role, status, pageable), Function.identity()));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('partner:view')")
    public ApiResponse<PartnerResponse> get(@AuthenticationPrincipal CurrentUser user, @PathVariable Long id) {
        return ApiResponse.ok(partnerService.get(user, id));
    }

    @Operation(summary = "Tra cứu doanh nghiệp theo mã số thuế",
            description = "D-66: gợi ý tên, địa chỉ để điền form thêm / sửa đối tác; không lưu dữ liệu. Mã sai định "
                    + "dạng: 400; không tìm thấy: 404 TAX_CODE_NOT_FOUND; tra quá nhiều: 429 TOO_MANY_REQUESTS; dịch "
                    + "vụ tắt hoặc lỗi: 503 TAX_CODE_LOOKUP_UNAVAILABLE; quá thời gian: 504 TAX_CODE_LOOKUP_TIMEOUT.")
    @GetMapping("/tax-code-lookup")
    @PreAuthorize("hasAuthority('partner:manage')")
    public ApiResponse<TaxCodeLookupResponse> lookupTaxCode(@RequestParam String taxCode) {
        return ApiResponse.ok(taxCodeLookupService.lookup(taxCode));
    }

    @Operation(summary = "Thêm mới", description = "Bắt buộc header Idempotency-Key.")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('partner:manage')")
    public ApiResponse<PartnerResponse> create(@AuthenticationPrincipal CurrentUser user,
                                      @RequestHeader(IdempotentCommand.HEADER) String idempotencyKey,
                                      @Valid @RequestBody PartnerRequest request) {
        return ApiResponse.ok(partnerService.create(user, idempotencyKey, request));
    }

    @Operation(summary = "Sửa", description = "Gửi version đã đọc; dữ liệu đã đổi: 409 CONCURRENT_MODIFICATION.")
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('partner:manage')")
    public ApiResponse<PartnerResponse> update(@AuthenticationPrincipal CurrentUser user, @PathVariable Long id,
                                      @Valid @RequestBody UpdatePartnerRequest request) {
        return ApiResponse.ok(partnerService.update(user, id, request));
    }

    @Operation(summary = "Ngừng hoạt động / kích hoạt lại")
    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAuthority('partner:manage')")
    public ApiResponse<PartnerResponse> changeStatus(@AuthenticationPrincipal CurrentUser user, @PathVariable Long id,
                                            @Valid @RequestBody ChangeStatusRequest request) {
        return ApiResponse.ok(partnerService.changeStatus(user, id, request));
    }
}
