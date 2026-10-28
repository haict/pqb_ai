package com.company.erp.ai.application.dto;

/** Kết quả gửi một câu hỏi: câu hỏi đã lưu và câu trả lời của trợ lý. */
public record AssistantExchangeResponse(AssistantMessageResponse question, AssistantMessageResponse answer) {
}
