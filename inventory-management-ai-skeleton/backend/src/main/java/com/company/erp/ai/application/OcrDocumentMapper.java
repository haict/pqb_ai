package com.company.erp.ai.application;

import com.company.erp.ai.application.ExtractedDocument.Field;
import com.company.erp.ai.application.dto.CatalogMatch;
import com.company.erp.ai.application.dto.OcrLine;
import com.company.erp.ai.application.dto.OcrTotals;
import com.company.erp.ai.application.dto.OcrValue;
import com.company.erp.masterdata.application.CatalogMatchingQueryService;
import com.company.erp.masterdata.application.CatalogMatchingQueryService.ProductUnitInfo;
import com.company.erp.masterdata.application.MasterDataQueryService.PartnerInfo;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Phần ánh xạ chung của form đơn mua và đơn bán (docs/14 mục 4.6): dòng hàng (hàng, ĐVT, thuế suất, kiểm tra số học),
 * tổng tiền, cảnh báo loại tiền và số dòng. Chỉ đọc danh mục (BR-AI-04).
 */
@Component
class OcrDocumentMapper {

    /** Bằng giới hạn số dòng của {@code PurchaseOrderRequest.lines} và {@code SalesOrderRequest.lines}. */
    static final int MAX_ORDER_LINES = 200;
    private static final String DEFAULT_CURRENCY = "VND";

    private final CatalogMatchingQueryService catalog;
    private final AiProperties properties;

    OcrDocumentMapper(CatalogMatchingQueryService catalog, AiProperties properties) {
        this.catalog = catalog;
        this.properties = properties;
    }

    /** Ảnh chụp danh mục của công ty cho một lần nhận dạng. */
    OcrCatalogMatcher matcher(Long companyId) {
        return new OcrCatalogMatcher(catalog.activeProducts(companyId), catalog.activeUnits(companyId),
                catalog.taxRates(companyId), properties.ocr().maxCandidates(), properties.ocr().nameSimilarity());
    }

    OcrAmountChecker checker() {
        return new OcrAmountChecker(properties.ocr().amountTolerance());
    }

    /**
     * Đối tác để đối chiếu (đã lọc công ty): cùng MST khi chứng từ có MST; không có MST thì mọi đối tác có vai trò
     * {@code role} để gợi ý theo tên (docs/14 mục 4.6).
     */
    List<PartnerInfo> partners(Long companyId, Field taxCode, String role) {
        return taxCode.present() ? catalog.activePartnersByTaxCode(companyId, taxCode.value())
                : catalog.activePartnersWithRole(companyId, role);
    }

    OcrValue currency(ExtractedDocument document, String formName, List<String> warnings) {
        OcrValue currency = OcrValue.of(document.header("currency"));
        if (currency.present() && !DEFAULT_CURRENCY.equals(currency.value())) {
            warnings.add("Chứng từ ghi loại tiền " + currency.value() + "; " + formName + " hiện chỉ dùng VND (D-24), "
                    + "kiểm tra lại đơn giá.");
        }
        return currency;
    }

    List<OcrLine> lines(ExtractedDocument document, LocalDate date, OcrCatalogMatcher matcher,
                        OcrAmountChecker checker) {
        List<CatalogMatch> products = document.lines().stream()
                .map(line -> matcher.product(ExtractedDocument.field(line, "code"), ExtractedDocument.field(line, "name")))
                .toList();
        List<Long> productIds = products.stream().filter(CatalogMatch::matched).map(CatalogMatch::id).distinct().toList();
        List<ProductUnitInfo> productUnits = catalog.productUnits(productIds, date);
        List<OcrLine> lines = new ArrayList<>();
        for (int i = 0; i < document.lines().size(); i++) {
            Map<String, Field> line = document.lines().get(i);
            OcrValue quantity = OcrValue.of(ExtractedDocument.field(line, "quantity"));
            OcrValue unitPrice = OcrValue.of(ExtractedDocument.field(line, "unitPrice"));
            OcrValue discount = OcrValue.of(ExtractedDocument.field(line, "discountPercent"));
            OcrValue printed = OcrValue.of(ExtractedDocument.field(line, "amount"));
            lines.add(new OcrLine(products.get(i),
                    matcher.unit(products.get(i), ExtractedDocument.field(line, "unit"), productUnits),
                    matcher.taxRate(ExtractedDocument.field(line, "taxRate"), date),
                    quantity, unitPrice, discount, printed, checker.line(quantity, unitPrice, discount, printed)));
        }
        return lines;
    }

    /** Cảnh báo không tìm thấy bảng hàng hóa hoặc vượt số dòng tối đa của form. */
    void lineWarnings(ExtractedDocument document, List<OcrLine> lines, String formName, List<String> warnings) {
        if (document.warnings().contains("TABLE_NOT_FOUND") || lines.isEmpty()) {
            warnings.add("Không tìm thấy bảng hàng hóa trên chứng từ; nhập dòng hàng bằng tay.");
        }
        if (lines.size() > MAX_ORDER_LINES) {
            warnings.add("Chứng từ có " + lines.size() + " dòng, " + formName + " tối đa " + MAX_ORDER_LINES
                    + " dòng.");
        }
    }

    OcrTotals totals(ExtractedDocument document, List<OcrLine> lines, OcrAmountChecker checker) {
        OcrValue goods = OcrValue.of(document.total("goods"));
        OcrValue vat = OcrValue.of(document.total("vat"));
        OcrValue total = OcrValue.of(document.total("total"));
        return new OcrTotals(goods, vat, total,
                checker.totals(lines.stream().map(OcrLine::printedAmount).toList(), goods, vat, total));
    }
}
