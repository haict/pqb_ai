package com.company.erp.ai.application;

import com.company.erp.ai.application.ExtractedDocument.Field;
import com.company.erp.ai.application.dto.CatalogMatch;
import com.company.erp.ai.application.dto.CatalogMatch.Candidate;
import com.company.erp.ai.domain.OcrTextKey;
import com.company.erp.masterdata.application.CatalogMatchingQueryService.ProductUnitInfo;
import com.company.erp.masterdata.application.MasterDataQueryService.PartnerInfo;
import com.company.erp.masterdata.application.MasterDataQueryService.ProductInfo;
import com.company.erp.masterdata.application.MasterDataQueryService.TaxRateInfo;
import com.company.erp.masterdata.application.MasterDataQueryService.UnitInfo;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Đối chiếu giá trị OCR với một ảnh chụp danh mục của công ty (BR-AI-06). Không truy cập database.
 *
 * <p>Chỉ tự gán khi khớp duy nhất: mã hàng, MST, ĐVT của hàng, tỷ lệ thuế. Tên hàng khớp chính xác duy nhất (sau khi
 * chuẩn hóa {@link OcrTextKey}) cũng được gán nhưng luôn {@code reviewRequired} (D-64, chủ dự án duyệt 2026-10-27).
 * Không khớp chính xác thì chỉ trả ứng viên gần giống, không gán.
 */
public class OcrCatalogMatcher {

    static final String SUPPLIER_ROLE = "SUPPLIER";
    static final String CUSTOMER_ROLE = "CUSTOMER";
    private static final String LOW_CONFIDENCE = "LOW_CONFIDENCE";

    private final List<ProductInfo> products;
    private final Map<String, ProductInfo> productsByCode;
    private final Map<Long, String> productKeys;
    private final List<UnitInfo> units;
    private final List<TaxRateInfo> taxRates;
    private final int maxCandidates;
    private final double nameSimilarity;

    public OcrCatalogMatcher(List<ProductInfo> products, List<UnitInfo> units, List<TaxRateInfo> taxRates,
                             int maxCandidates, double nameSimilarity) {
        this.products = List.copyOf(products);
        this.productsByCode = products.stream()
                .collect(Collectors.toMap(p -> p.code().toUpperCase(), Function.identity(), (a, b) -> a));
        this.productKeys = products.stream().collect(Collectors.toMap(ProductInfo::id, p -> OcrTextKey.of(p.name())));
        this.units = List.copyOf(units);
        this.taxRates = List.copyOf(taxRates);
        this.maxCandidates = maxCandidates;
        this.nameSimilarity = nameSimilarity;
    }

    /**
     * Nhà cung cấp đang hoạt động có MST trên chứng từ ({@code partners}: đối tác cùng MST, đã lọc công ty). Chứng từ
     * không có MST: {@code partners} là mọi đối tác có vai trò, chỉ trả ứng viên theo tên, không tự gán (docs/14 mục 4.6).
     */
    public CatalogMatch supplier(Field taxCode, Field name, List<PartnerInfo> partners) {
        return partner(SUPPLIER_ROLE, taxCode, name, partners);
    }

    /** Khách hàng đang hoạt động có MST trên phiếu đặt hàng của khách (AI-2). */
    public CatalogMatch customer(Field taxCode, Field name, List<PartnerInfo> partners) {
        return partner(CUSTOMER_ROLE, taxCode, name, partners);
    }

    private CatalogMatch partner(String role, Field taxCode, Field name, List<PartnerInfo> partners) {
        String raw = describe(name, taxCode);
        List<PartnerInfo> found = partners.stream()
                .filter(p -> p.active() && p.roleCodes().contains(role)).toList();
        if (!taxCode.present()) {
            return name.present() ? partnerByName(name, found) : CatalogMatch.unknown(raw);
        }
        if (found.size() == 1) {
            PartnerInfo partner = found.get(0);
            return CatalogMatch.matched("TAX_CODE", lowConfidence(taxCode), partner.id(), partner.code(),
                    partner.name(), raw);
        }
        if (found.isEmpty()) {
            return CatalogMatch.notFound(raw);
        }
        return CatalogMatch.ambiguous(raw, found.stream().limit(maxCandidates)
                .map(p -> new Candidate(p.id(), p.code(), p.name())).toList());
    }

    /** Tên giống đến ngưỡng {@code nameSimilarity}: chỉ là ứng viên, kể cả khi khớp duy nhất (BR-AI-06). */
    private CatalogMatch partnerByName(Field name, List<PartnerInfo> partners) {
        String key = OcrTextKey.of(name.value());
        List<Candidate> similar = partners.stream()
                .map(p -> Map.entry(p, OcrTextKey.similarity(key, OcrTextKey.of(p.name()))))
                .filter(entry -> entry.getValue() >= nameSimilarity)
                .sorted(Map.Entry.<PartnerInfo, Double>comparingByValue().reversed())
                .limit(maxCandidates)
                .map(entry -> new Candidate(entry.getKey().id(), entry.getKey().code(), entry.getKey().name()))
                .toList();
        return similar.isEmpty() ? CatalogMatch.notFound(name.value()) : CatalogMatch.ambiguous(name.value(), similar);
    }

    public CatalogMatch product(Field code, Field name) {
        if (code.present()) {
            ProductInfo byCode = productsByCode.get(code.value().trim().toUpperCase());
            if (byCode != null) {
                return CatalogMatch.matched("CODE", lowConfidence(code), byCode.id(), byCode.code(), byCode.name(),
                        describe(name, code));
            }
        }
        if (!name.present()) {
            return code.present() ? CatalogMatch.notFound(code.value()) : CatalogMatch.unknown(null);
        }
        String key = OcrTextKey.of(name.value());
        List<ProductInfo> exact = products.stream().filter(p -> productKeys.get(p.id()).equals(key)).toList();
        if (exact.size() == 1) {
            ProductInfo product = exact.get(0);
            return CatalogMatch.matched("NAME", true, product.id(), product.code(), product.name(), name.value());
        }
        if (exact.size() > 1) {
            return CatalogMatch.ambiguous(name.value(), exact.stream().limit(maxCandidates)
                    .map(OcrCatalogMatcher::candidate).toList());
        }
        List<Candidate> similar = products.stream()
                .map(p -> Map.entry(p, OcrTextKey.similarity(key, productKeys.get(p.id()))))
                .filter(entry -> entry.getValue() >= nameSimilarity)
                .sorted(Map.Entry.<ProductInfo, Double>comparingByValue().reversed())
                .limit(maxCandidates)
                .map(entry -> candidate(entry.getKey()))
                .toList();
        return similar.isEmpty() ? CatalogMatch.notFound(name.value()) : CatalogMatch.ambiguous(name.value(), similar);
    }

    /** ĐVT trong các đơn vị của hàng đã gán (ĐVT cơ sở và đơn vị quy đổi còn hiệu lực). Không khớp thì để trống. */
    public CatalogMatch unit(CatalogMatch product, Field unit, List<ProductUnitInfo> productUnits) {
        if (!unit.present()) {
            return CatalogMatch.unknown(null);
        }
        if (!product.matched()) {
            return CatalogMatch.unknown(unit.value());
        }
        ProductInfo info = products.stream().filter(p -> p.id().equals(product.id())).findFirst().orElseThrow();
        Set<Long> allowed = new HashSet<>();
        allowed.add(info.baseUnitId());
        productUnits.stream().filter(u -> u.productId().equals(info.id())).forEach(u -> allowed.add(u.unitId()));
        String key = OcrTextKey.of(unit.value());
        List<UnitInfo> found = units.stream()
                .filter(u -> allowed.contains(u.id()))
                .filter(u -> OcrTextKey.of(u.name()).equals(key) || OcrTextKey.of(u.code()).equals(key))
                .toList();
        if (found.size() == 1) {
            UnitInfo match = found.get(0);
            return CatalogMatch.matched("UNIT", lowConfidence(unit), match.id(), match.code(), match.name(),
                    unit.value());
        }
        return found.isEmpty() ? CatalogMatch.notFound(unit.value()) : CatalogMatch.ambiguous(unit.value(),
                found.stream().map(u -> new Candidate(u.id(), u.code(), u.name())).toList());
    }

    /** Thuế suất có tỷ lệ bằng giá trị OCR, còn hiệu lực tại ngày đơn. */
    public CatalogMatch taxRate(Field rate, LocalDate date) {
        if (!rate.present()) {
            return CatalogMatch.unknown(null);
        }
        BigDecimal value;
        try {
            value = new BigDecimal(rate.value());
        } catch (NumberFormatException ex) {
            return CatalogMatch.notFound(rate.value());
        }
        List<TaxRateInfo> found = taxRates.stream()
                .filter(t -> t.effectiveOn(date) && t.rate().compareTo(value) == 0)
                .sorted(Comparator.comparing(TaxRateInfo::code))
                .toList();
        String raw = rate.rawText() != null ? rate.rawText() : rate.value() + "%";
        if (found.size() == 1) {
            TaxRateInfo match = found.get(0);
            return CatalogMatch.matched("RATE", lowConfidence(rate), match.id(), match.code(), match.name(), raw);
        }
        return found.isEmpty() ? CatalogMatch.notFound(raw) : CatalogMatch.ambiguous(raw,
                found.stream().map(t -> new Candidate(t.id(), t.code(), t.name())).toList());
    }

    private static Candidate candidate(ProductInfo product) {
        return new Candidate(product.id(), product.code(), product.name());
    }

    private static boolean lowConfidence(Field field) {
        return LOW_CONFIDENCE.equals(field.status());
    }

    private static String describe(Field name, Field key) {
        if (name.present() && key.present()) {
            return name.value() + " (" + key.value() + ")";
        }
        return name.present() ? name.value() : key.value();
    }
}
