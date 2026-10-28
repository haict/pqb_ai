package com.company.erp.ai.application.dto;

import com.company.erp.ai.domain.OcrDocumentType;
import java.util.List;

/**
 * Gợi ý điền form đơn bán từ phiếu đặt hàng của khách (AI-2, docs/14 mục 4.5). Chỉ là gợi ý: không lưu gì (BR-AI-04);
 * đơn giá trống thì khi lưu server lấy theo bảng giá như nhập tay.
 */
public record SalesOcrResponse(OcrDocumentType documentType, int pageCount, Header header, List<OcrLine> lines,
                               OcrTotals totals, List<String> warnings) implements OcrRecognitionResponse {

    public SalesOcrResponse {
        lines = List.copyOf(lines);
        warnings = List.copyOf(warnings);
    }

    /**
     * @param customer        khách hàng theo MST
     * @param description     "Theo đơn đặt hàng của khách số ..."
     * @param deliveryAddress địa chỉ giao đọc từ chứng từ; văn bản có thể mất dấu (TEXT_DIACRITICS_UNRELIABLE) nên
     *                        frontend chỉ hiển thị để người dùng đối chiếu, không tự điền
     */
    public record Header(CatalogMatch customer, OcrValue documentDate, OcrValue description, OcrValue deliveryAddress,
                         OcrValue currencyCode) {
    }
}
