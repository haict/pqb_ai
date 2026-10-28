package com.company.erp.ai.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.company.erp.ai.application.ExtractedDocument.Field;
import com.company.erp.ai.application.dto.CatalogMatch;
import com.company.erp.masterdata.application.CatalogMatchingQueryService.ProductUnitInfo;
import com.company.erp.masterdata.application.MasterDataQueryService.PartnerInfo;
import com.company.erp.masterdata.application.MasterDataQueryService.ProductInfo;
import com.company.erp.masterdata.application.MasterDataQueryService.TaxRateInfo;
import com.company.erp.masterdata.application.MasterDataQueryService.UnitInfo;
import com.company.erp.masterdata.domain.PartnerRole;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class OcrCatalogMatcherTest {

    private static final LocalDate DATE = LocalDate.of(2026, 10, 20);
    private static final long HOP = 1L;
    private static final long LO = 2L;
    private static final long CAI = 3L;

    private final OcrCatalogMatcher matcher = new OcrCatalogMatcher(
            List.of(product(10, "GL8S104", "Triglycerides (12x65ml)(Glenbio/Anh)", HOP),
                    product(11, "GLM114", "Triglycerides (12x65ml)(Glenbio/Anh)", HOP),
                    product(12, "GL8S605", "LDL Cholesterol Direct (6x40ml/4x20ml/1x1ml)(Glenbio/Anh)", HOP),
                    product(13, "VT_ONG", "Ống lấy máu chân không", CAI)),
            List.of(new UnitInfo(HOP, "HOP", "Hộp", true), new UnitInfo(LO, "LO", "Lọ", true),
                    new UnitInfo(CAI, "CAI", "Cái", true)),
            List.of(tax(20, "VAT5", "5"), tax(21, "VAT10", "10"),
                    new TaxRateInfo(22L, 1L, "VAT10_OLD", "Cũ", new BigDecimal("10"), LocalDate.of(2020, 1, 1),
                            LocalDate.of(2025, 12, 31), true)),
            10, 0.85);

    @Test
    @DisplayName("BR-AI-06: khớp mã hàng thì gán, không cần xác nhận")
    void productByCode() {
        CatalogMatch match = matcher.product(ok("gl8s605"), ok("bất kỳ"));
        assertThat(match.status()).isEqualTo("MATCHED");
        assertThat(match.matchedBy()).isEqualTo("CODE");
        assertThat(match.id()).isEqualTo(12L);
        assertThat(match.reviewRequired()).isFalse();
    }

    @Test
    @DisplayName("D-64: tên khớp duy nhất (đã bỏ chữ model không đọc được) thì gán nhưng phải xác nhận")
    void productByUniqueNameRequiresReview() {
        CatalogMatch match = matcher.product(Field.UNKNOWN, ok("ng ly máu chân không"));
        assertThat(match.matchedBy()).isEqualTo("NAME");
        assertThat(match.id()).isEqualTo(13L);
        assertThat(match.reviewRequired()).isTrue();
    }

    @Test
    @DisplayName("BR-AI-06: tên trùng nhiều mã thì không tự chọn, trả ứng viên")
    void duplicateNameIsAmbiguous() {
        CatalogMatch match = matcher.product(Field.UNKNOWN, ok("Triglycerides (12x65ml)(Glenbio/Anh)"));
        assertThat(match.status()).isEqualTo("AMBIGUOUS");
        assertThat(match.id()).isNull();
        assertThat(match.candidates()).extracting(CatalogMatch.Candidate::code).containsExactly("GL8S104", "GLM114");
    }

    @Test
    @DisplayName("Tên gần giống chỉ là ứng viên; mã NCC lạ và tên lạ thì không tìm thấy")
    void similarAndUnknownNames() {
        CatalogMatch similar = matcher.product(ok("SM-0045"), ok("LDL Cholesterol Direct (6x40ml/4x20ml)(Glenbio/Anh)"));
        assertThat(similar.status()).isEqualTo("AMBIGUOUS");
        assertThat(similar.candidates()).extracting(CatalogMatch.Candidate::code).containsExactly("GL8S605");

        assertThat(matcher.product(ok("SM-0045"), ok("Que thử đường huyết")).status()).isEqualTo("NOT_FOUND");
        assertThat(matcher.product(Field.UNKNOWN, Field.UNKNOWN).status()).isEqualTo("UNKNOWN");
    }

    @Test
    @DisplayName("ĐVT chỉ khớp trong đơn vị của hàng: 'Hp' = Hộp; đơn vị quy đổi; ngoài danh sách thì để trống")
    void unitWithinProductUnits() {
        CatalogMatch product = matcher.product(ok("GL8S605"), Field.UNKNOWN);
        assertThat(matcher.unit(product, ok("Hp"), List.of()).code()).isEqualTo("HOP");
        assertThat(matcher.unit(product, ok("Lo"), List.of()).status()).isEqualTo("NOT_FOUND");
        assertThat(matcher.unit(product, ok("Lo"), List.of(new ProductUnitInfo(12L, LO, BigDecimal.TEN))).code())
                .isEqualTo("LO");
        assertThat(matcher.unit(CatalogMatch.notFound("x"), ok("Hp"), List.of()).status()).isEqualTo("UNKNOWN");
    }

    @Test
    @DisplayName("Thuế suất theo tỷ lệ còn hiệu lực; 8% không có trong danh mục thì NOT_FOUND")
    void taxRateByEffectiveRate() {
        assertThat(matcher.taxRate(ok("10"), DATE).code()).isEqualTo("VAT10");
        assertThat(matcher.taxRate(ok("10"), LocalDate.of(2024, 1, 1)).status()).isEqualTo("AMBIGUOUS");
        assertThat(matcher.taxRate(ok("8"), DATE).status()).isEqualTo("NOT_FOUND");
        assertThat(matcher.taxRate(Field.UNKNOWN, DATE).status()).isEqualTo("UNKNOWN");
    }

    @Test
    @DisplayName("Nhà cung cấp theo MST: 1 / 0 / nhiều; đối tác chỉ là khách hàng thì không tính")
    void supplierByTaxCode() {
        PartnerInfo supplier = partner(1, "NCC_01", Set.of("SUPPLIER"));
        PartnerInfo customer = partner(2, "KH_01", Set.of("CUSTOMER"));
        PartnerInfo branch = partner(3, "NCC_01_CN", Set.of("SUPPLIER", "CUSTOMER"));
        Field mst = ok("0300000001");

        assertThat(matcher.supplier(mst, ok("NPP"), List.of(supplier, customer)).id()).isEqualTo(1L);
        assertThat(matcher.supplier(mst, ok("NPP"), List.of(customer)).status()).isEqualTo("NOT_FOUND");
        assertThat(matcher.supplier(mst, ok("NPP"), List.of(supplier, branch)).status()).isEqualTo("AMBIGUOUS");
        assertThat(matcher.supplier(Field.UNKNOWN, Field.UNKNOWN, List.of(supplier)).status()).isEqualTo("UNKNOWN");
    }

    @Test
    @DisplayName("docs/14 mục 4.6: không có MST thì chỉ trả ứng viên theo tên, kể cả khớp duy nhất; không tự gán")
    void partnerWithoutTaxCodeOnlyCandidates() {
        PartnerInfo supplier = partner(1, "NCC_01", Set.of("SUPPLIER"));
        PartnerInfo customer = partner(2, "NCC_01X", Set.of("CUSTOMER"));

        CatalogMatch byName = matcher.supplier(Field.UNKNOWN, ok("NCC_01"), List.of(supplier, customer));
        assertThat(byName.status()).isEqualTo("AMBIGUOUS");
        assertThat(byName.id()).isNull();
        assertThat(byName.candidates()).extracting(CatalogMatch.Candidate::id).containsExactly(1L);
        assertThat(matcher.supplier(Field.UNKNOWN, ok("Công ty khác hẳn"), List.of(supplier)).status())
                .isEqualTo("NOT_FOUND");
    }

    @Test
    @DisplayName("AI-2: khách hàng theo MST; đối tác chỉ là nhà cung cấp thì không tính")
    void customerByTaxCode() {
        PartnerInfo supplier = partner(1, "NCC_01", Set.of("SUPPLIER"));
        PartnerInfo customer = partner(2, "KH_01", Set.of("CUSTOMER"));
        PartnerInfo both = partner(3, "NCC_01_CN", Set.of("SUPPLIER", "CUSTOMER"));
        Field mst = ok("0300000001");

        assertThat(matcher.customer(mst, ok("BV"), List.of(supplier, customer)).id()).isEqualTo(2L);
        assertThat(matcher.customer(mst, ok("BV"), List.of(supplier)).status()).isEqualTo("NOT_FOUND");
        assertThat(matcher.customer(mst, ok("BV"), List.of(customer, both)).status()).isEqualTo("AMBIGUOUS");
    }

    @Test
    @DisplayName("Giá trị OCR độ tin cậy thấp: khớp khóa vẫn gán nhưng phải xác nhận")
    void lowConfidenceKeyRequiresReview() {
        Field mst = new Field("0300000001", "0300000001", null, "OCR", 0.6, "LOW_CONFIDENCE", 1, null, List.of());
        assertThat(matcher.supplier(mst, Field.UNKNOWN, List.of(partner(1, "NCC_01", Set.of("SUPPLIER"))))
                .reviewRequired()).isTrue();
    }

    private static Field ok(String value) {
        return new Field(value, value, null, "OCR", 0.99, "OK", 1, null, List.of());
    }

    private static ProductInfo product(long id, String code, String name, long baseUnitId) {
        return new ProductInfo(id, 1L, code, name, null, baseUnitId, true, true, false, true, true, null, null);
    }

    private static TaxRateInfo tax(long id, String code, String rate) {
        return new TaxRateInfo(id, 1L, code, code, new BigDecimal(rate), LocalDate.of(2020, 1, 1), null, true);
    }

    private static PartnerInfo partner(long id, String code, Set<String> roles) {
        Set<PartnerRole> partnerRoles = roles.stream().map(PartnerRole::valueOf).collect(Collectors.toSet());
        return new PartnerInfo(id, 1L, code, code, partnerRoles, null, true, null, null, "0300000001", null, null);
    }
}
