package com.company.erp.ai.application.dto;

import com.company.erp.ai.domain.OcrDocumentType;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * Kết quả nhận dạng một chứng từ: gợi ý cho form đơn mua hoặc đơn bán tùy {@code documentType} (docs/14 mục 6.1). Chỉ
 * là gợi ý, không lưu gì (BR-AI-04).
 */
@Schema(oneOf = {PurchaseOcrResponse.class, SalesOcrResponse.class})
public sealed interface OcrRecognitionResponse permits PurchaseOcrResponse, SalesOcrResponse {

    OcrDocumentType documentType();

    int pageCount();

    List<OcrLine> lines();

    OcrTotals totals();

    List<String> warnings();
}
