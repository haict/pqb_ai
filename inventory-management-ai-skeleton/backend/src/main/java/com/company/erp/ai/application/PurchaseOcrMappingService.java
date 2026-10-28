package com.company.erp.ai.application;

import com.company.erp.ai.application.ExtractedDocument.Field;
import com.company.erp.ai.application.dto.CatalogMatch;
import com.company.erp.ai.application.dto.OcrLine;
import com.company.erp.ai.application.dto.OcrValue;
import com.company.erp.ai.application.dto.PurchaseOcrResponse;
import com.company.erp.ai.application.dto.PurchaseOcrResponse.Header;
import com.company.erp.ai.domain.OcrDocumentType;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * Chuyển kết quả nhận dạng thành gợi ý cho form đơn mua (docs/14 mục 4.5, 4.6): tra nhà cung cấp, hàng, ĐVT, thuế suất,
 * kiểm tra số học. Chỉ đọc danh mục; không tạo hay sửa dữ liệu (BR-AI-04).
 */
@Service
public class PurchaseOcrMappingService {

    private static final String FORM_NAME = "đơn mua";

    private final OcrDocumentMapper mapper;

    PurchaseOcrMappingService(OcrDocumentMapper mapper) {
        this.mapper = mapper;
    }

    public PurchaseOcrResponse map(Long companyId, LocalDate date, OcrDocumentType type, ExtractedDocument document) {
        OcrCatalogMatcher matcher = mapper.matcher(companyId);
        OcrAmountChecker checker = mapper.checker();
        List<String> warnings = new ArrayList<>();
        Header header = header(companyId, type, document, matcher, warnings);
        List<OcrLine> lines = mapper.lines(document, date, matcher, checker);
        mapper.lineWarnings(document, lines, FORM_NAME, warnings);
        return new PurchaseOcrResponse(type, document.pageCount(), header, lines,
                mapper.totals(document, lines, checker), warnings);
    }

    private Header header(Long companyId, OcrDocumentType type, ExtractedDocument document, OcrCatalogMatcher matcher,
                          List<String> warnings) {
        Field taxCode = document.header("partnerTaxCode");
        CatalogMatch supplier = matcher.supplier(taxCode, document.header("partnerName"),
                mapper.partners(companyId, taxCode, OcrCatalogMatcher.SUPPLIER_ROLE));
        OcrValue number = OcrValue.of(document.header("documentNo"));
        OcrValue date = OcrValue.of(document.header("documentDate"));
        OcrValue currency = mapper.currency(document, FORM_NAME, warnings);
        if (type == OcrDocumentType.PURCHASE_INVOICE) {
            return new Header(supplier, OcrValue.of(document.header("invoiceSeries")), number, date, OcrValue.UNKNOWN,
                    OcrValue.UNKNOWN, currency);
        }
        OcrValue description = number.present()
                ? number.withValue("Theo đơn đặt hàng số " + number.value()) : OcrValue.UNKNOWN;
        return new Header(supplier, OcrValue.UNKNOWN, OcrValue.UNKNOWN, OcrValue.UNKNOWN, date, description, currency);
    }
}
