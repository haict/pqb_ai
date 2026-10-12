package com.company.erp.accounting.api;

import com.company.erp.accounting.application.AccountImportService;
import com.company.erp.accounting.application.AccountService;
import com.company.erp.accounting.application.dto.AccountImportResponse;
import com.company.erp.accounting.application.dto.AccountRequest;
import com.company.erp.accounting.application.dto.AccountResponse;
import com.company.erp.accounting.application.dto.UpdateAccountRequest;
import com.company.erp.common.api.ApiResponse;
import com.company.erp.common.api.PageResponse;
import com.company.erp.common.application.IdempotentCommand;
import com.company.erp.common.domain.RecordStatus;
import com.company.erp.system.application.CurrentUser;
import com.company.erp.system.application.dto.ChangeStatusRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
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

/** Hệ thống tài khoản (BR-ACC-01). */
@Tag(name = "Hệ thống tài khoản")
@RestController
@RequestMapping("/api/v1/accounts")
public class AccountController {

    private static final MediaType XLSX =
            MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

    private final AccountService accountService;
    private final AccountImportService importService;

    public AccountController(AccountService accountService, AccountImportService importService) {
        this.accountService = accountService;
        this.importService = importService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('account:view')")
    public ApiResponse<PageResponse<AccountResponse>> search(@AuthenticationPrincipal CurrentUser user,
            @RequestParam Long companyId, @RequestParam(required = false) String keyword,
            @RequestParam(required = false) RecordStatus status, @RequestParam(required = false) Boolean postable,
            Pageable pageable) {
        return ApiResponse.ok(PageResponse.from(accountService.search(user, companyId, keyword, status, postable,
                pageable), Function.identity()));
    }

    @Operation(summary = "Toàn bộ tài khoản theo mã (hiển thị dạng cây)",
            description = "Không phân trang: số tài khoản của một công ty có giới hạn.")
    @GetMapping("/tree")
    @PreAuthorize("hasAuthority('account:view')")
    public ApiResponse<List<AccountResponse>> tree(@AuthenticationPrincipal CurrentUser user,
                                                   @RequestParam Long companyId) {
        return ApiResponse.ok(accountService.tree(user, companyId));
    }

    @Operation(summary = "Thêm tài khoản", description = "Bắt buộc header Idempotency-Key. Tài khoản cha đã "
            + "phát sinh hạch toán: 409 DATA_IN_USE.")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('account:manage')")
    public ApiResponse<AccountResponse> create(@AuthenticationPrincipal CurrentUser user,
                                               @RequestHeader(IdempotentCommand.HEADER) String idempotencyKey,
                                               @Valid @RequestBody AccountRequest request) {
        return ApiResponse.ok(accountService.create(user, idempotencyKey, request));
    }

    @Operation(summary = "Sửa tài khoản", description = "Không đổi mã, tài khoản cha. 409 CONCURRENT_MODIFICATION.")
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('account:manage')")
    public ApiResponse<AccountResponse> update(@AuthenticationPrincipal CurrentUser user, @PathVariable Long id,
                                               @Valid @RequestBody UpdateAccountRequest request) {
        return ApiResponse.ok(accountService.update(user, id, request));
    }

    @Operation(summary = "Ngừng hoạt động / kích hoạt lại")
    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAuthority('account:manage')")
    public ApiResponse<AccountResponse> changeStatus(@AuthenticationPrincipal CurrentUser user,
                                                     @PathVariable Long id,
                                                     @Valid @RequestBody ChangeStatusRequest request) {
        return ApiResponse.ok(accountService.changeStatus(user, id, request));
    }

    @Operation(summary = "Tải file mẫu nhập hệ thống tài khoản (.xlsx)")
    @GetMapping("/import-template")
    @PreAuthorize("hasAuthority('account:manage')")
    public ResponseEntity<byte[]> importTemplate() {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"mau-he-thong-tai-khoan.xlsx\"")
                .contentType(XLSX)
                .body(importService.template());
    }

    @Operation(summary = "Nhập hệ thống tài khoản từ Excel",
            description = "Tất cả hoặc không: có lỗi thì trả 400 kèm lỗi theo dòng. Bắt buộc Idempotency-Key.")
    @PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('account:manage')")
    public ApiResponse<AccountImportResponse> importAccounts(@AuthenticationPrincipal CurrentUser user,
            @RequestHeader(IdempotentCommand.HEADER) String idempotencyKey, @RequestParam Long companyId,
            @RequestPart("file") MultipartFile file) {
        return ApiResponse.ok(importService.importAccounts(user, companyId, idempotencyKey, file));
    }
}
