package com.company.erp.ai.application.dto;

/** Tổng tiền in trên chứng từ và kết quả kiểm tra với các dòng (BR-AI-07); chỉ để cảnh báo, không gửi khi lưu. */
public record OcrTotals(OcrValue goods, OcrValue vat, OcrValue total, AmountCheck check) {
}
