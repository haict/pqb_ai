package com.company.erp.ai.application.dto;

/** Trạng thái chức năng AI để giao diện ẩn / hiện nút nhận dạng chứng từ, trợ lý (BR-AI-09). */
public record AiHealthResponse(boolean ocrAvailable, boolean chatAvailable) {
}
