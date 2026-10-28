package com.company.erp.ai.application;

import com.company.erp.ai.application.ExtractedDocument.Field;
import com.company.erp.ai.application.dto.CatalogMatch;
import com.company.erp.ai.application.dto.OcrLine;
import com.company.erp.ai.application.dto.OcrValue;
import com.company.erp.ai.application.dto.SalesOcrResponse;
import com.company.erp.ai.application.dto.SalesOcrResponse.Header;
import com.company.erp.ai.domain.OcrDocumentType;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * Chuyển phiếu đặt hàng của khách thành gợi ý cho form đơn bán (AI-2, docs/14 mục 4.5, 4.6): tra khách hàng theo MST,
 * hàng, ĐVT, thuế suất, kiểm tra số học. Chỉ đọc danh mục; không tạo hay sửa dữ liệu (BR-AI-04).
 */
@Service
public class SalesOcrMappingService {

    private static final String FORM_NAME = "đơn bán";

    private final OcrDocumentMapper mapper;

    SalesOcrMappingService(OcrDocumentMapper mapper) {
        this.mapper = mapper;
    }

    public SalesOcrResponse map(Long companyId, LocalDate date, ExtractedDocument document) {
        OcrCatalogMatcher matcher = mapper.matcher(companyId);
        OcrAmountChecker checker = mapper.checker();
        List<String> warnings = new ArrayList<>();
        Field taxCode = document.header("partnerTaxCode");
        CatalogMatch customer = matcher.customer(taxCode, document.header("partnerName"),
                mapper.partners(companyId, taxCode, OcrCatalogMatcher.CUSTOMER_ROLE));
        OcrValue number = OcrValue.of(document.header("documentNo"));
        OcrValue description = number.present()
                ? number.withValue("Theo đơn đặt hàng của khách số " + number.value()) : OcrValue.UNKNOWN;
        Header header = new Header(customer, OcrValue.of(document.header("documentDate")), description,
                OcrValue.of(document.header("deliveryAddress")), mapper.currency(document, FORM_NAME, warnings));
        List<OcrLine> lines = mapper.lines(document, date, matcher, checker);
        mapper.lineWarnings(document, lines, FORM_NAME, warnings);
        return new SalesOcrResponse(OcrDocumentType.SALES_ORDER, document.pageCount(), header, lines,
                mapper.totals(document, lines, checker), warnings);
    }
}
