package com.company.erp.ai.application.dto;

/**
 * Gợi ý một dòng hàng từ chứng từ nhận dạng, dùng chung cho form đơn mua và đơn bán (docs/14 mục 4.5).
 * {@code printedAmount}: thành tiền in trên chứng từ, chỉ để kiểm tra; khi lưu server tự tính.
 */
public record OcrLine(CatalogMatch product, CatalogMatch unit, CatalogMatch taxRate, OcrValue quantity,
                      OcrValue unitPrice, OcrValue discountPercent, OcrValue printedAmount, AmountCheck amountCheck) {
}
