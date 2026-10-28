package com.company.erp.ai.api;

import com.company.erp.ai.application.AiAvailability;
import com.company.erp.ai.application.dto.AiHealthResponse;
import com.company.erp.common.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Nhận dạng chứng từ (OCR)")
@RestController
@RequestMapping("/api/v1/ai")
public class AiHealthController {

    private final AiAvailability availability;

    public AiHealthController(AiAvailability availability) {
        this.availability = availability;
    }

    @Operation(summary = "Trạng thái chức năng AI",
            description = "Để giao diện ẩn / hiện nút nhận dạng, trợ lý; không lộ địa chỉ, model của AI Service.")
    @GetMapping("/health")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<AiHealthResponse> health() {
        return ApiResponse.ok(new AiHealthResponse(availability.ocrAvailable(), availability.chatAvailable()));
    }
}
