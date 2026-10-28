package com.company.erp.ai.application.dto;

import com.company.erp.ai.domain.OcrDocumentType;
import java.util.List;

/**
 * Gợi ý điền form đơn mua từ chứng từ nhận dạng (docs/14 mục 4.5, 6.1). Chỉ là gợi ý: không lưu gì (BR-AI-04); người
 * dùng kiểm tra rồi lưu, server kiểm tra lại như nhập tay.
 */
public record PurchaseOcrResponse(OcrDocumentType documentType, int pageCount, Header header, List<OcrLine> lines,
                                  OcrTotals totals, List<String> warnings) implements OcrRecognitionResponse {

    public PurchaseOcrResponse {
        lines = List.copyOf(lines);
        warnings = List.copyOf(warnings);
    }

    /**
     * @param supplier     nhà cung cấp theo MST
     * @param invoiceNo    số hóa đơn (chỉ khi là hóa đơn NCC)
     * @param documentDate ngày đơn (chỉ khi là đơn đặt hàng gửi NCC)
     * @param description  "Theo đơn đặt hàng số ..." (chỉ khi là đơn đặt hàng gửi NCC)
     */
    public record Header(CatalogMatch supplier, OcrValue invoiceSeries, OcrValue invoiceNo, OcrValue invoiceDate,
                         OcrValue documentDate, OcrValue description, OcrValue currencyCode) {
    }
}
