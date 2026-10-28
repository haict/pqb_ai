package com.company.erp.ai.api;

import com.company.erp.ai.application.OcrRecognitionService;
import com.company.erp.ai.application.dto.OcrRecognitionResponse;
import com.company.erp.ai.domain.OcrDocumentType;
import com.company.erp.common.api.ApiResponse;
import com.company.erp.system.application.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** Nhận dạng chứng từ (OCR, D-64) trả gợi ý điền form; không lưu chứng từ, không lưu file. */
@Tag(name = "Nhận dạng chứng từ (OCR)")
@RestController
@RequestMapping("/api/v1/ocr")
public class OcrRecognitionController {

    private final OcrRecognitionService service;

    public OcrRecognitionController(OcrRecognitionService service) {
        this.service = service;
    }

    @Operation(summary = "Nhận dạng chứng từ để điền form đơn mua hoặc đơn bán",
            description = "Multipart: file JPG / PNG / PDF. documentType PURCHASE_INVOICE, PURCHASE_ORDER (form đơn mua, "
                    + "cần purchase-order:create) hoặc SALES_ORDER (phiếu đặt hàng của khách, form đơn bán, cần "
                    + "sales-order:create); luôn cần ocr:use; công ty, chi nhánh trong phạm vi. Chỉ trả gợi ý, không tạo dữ liệu nên "
                    + "không cần Idempotency-Key (Q-06). Lỗi: 403 FORBIDDEN (thiếu quyền form đích), 400 OCR_FILE_INVALID, 413 OCR_FILE_TOO_LARGE, 415 OCR_UNSUPPORTED_TYPE, 422 OCR_NO_TEXT, "
                    + "429 AI_BUSY, 503 AI_SERVICE_UNAVAILABLE, 504 AI_TIMEOUT.")
    @PostMapping(value = "/recognitions", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('ocr:use')")
    public ApiResponse<OcrRecognitionResponse> recognize(@AuthenticationPrincipal CurrentUser user,
            @RequestParam Long companyId, @RequestParam Long branchId,
            @RequestParam OcrDocumentType documentType,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate documentDate,
            @RequestPart("file") MultipartFile file) {
        return ApiResponse.ok(service.recognize(user,
                new OcrRecognitionService.Command(companyId, branchId, documentType, documentDate), file));
    }
}
