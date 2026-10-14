package com.company.erp.purchasing;

import static com.company.erp.support.ApiTestSupport.login;
import static com.company.erp.support.ApiTestSupport.newKey;
import static com.company.erp.support.ApiTestSupport.unique;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.company.erp.support.ApiTestSupport.Session;
import com.company.erp.support.MySqlIntegrationTest;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * Sổ chi tiết mua hàng (hiện trạng 14-15) trên MySQL thật: dòng đơn mua, trả hàng, giảm giá, dòng đảo; tổng khớp
 * chứng từ (AT-12); lọc NV mua hàng (người lập); quyền và phạm vi chi nhánh (AT-10).
 */
@MySqlIntegrationTest
@AutoConfigureMockMvc
class PurchaseLedgerIntegrationTest extends PurchasingIntegrationSupport {

    private static final String LEDGER = "/api/v1/purchasing/reports/detail-ledger";
    private static final String RETURNS = "/api/v1/purchase-returns";
    private static final String THN = "$.data.lines[?(@.documentType == 'THN')]";
    private static final String GGM = "$.data.lines[?(@.documentType == 'GGM')]";

    @Autowired
    PurchaseLedgerIntegrationTest(MockMvc mockMvc, JdbcTemplate jdbc) {
        super(mockMvc, jdbc);
    }

    @Test
    @DisplayName("AT-12, AT-09: đơn mua vào cột mua, trả hàng / giảm giá vào cột riêng, giảm giá đã đảo có dòng âm")
    void purchaseReturnAndReversal() throws Exception {
        long item = newProduct();
        long supplier = partner("NCC_01");
        long order = postedOrder(orderWithInvoice(supplier, warehouse("KHO_HN"), unique("C26T"), "88",
                List.of(orderLine(item, unit("CAI"), "3", "500000", "10", null))));
        long orderLine = orderLineId(order, 1);
        long goodsReturn = postedReturn(returnBody("THN", supplier, order,
                "{\"orderLineId\":" + orderLine + ",\"quantity\":1}"));
        long discount = postedReturn(returnBody("GGM", supplier, order,
                "{\"orderLineId\":" + orderLine + ",\"amount\":200000}"));
        act(chief, RETURNS, discount, "reverse", "{\"reason\":\"Nhập nhầm\"}")
                .andExpect(jsonPath("$.data.status").value("REVERSED"));
        BigDecimal returned = jdbc.queryForObject("SELECT amount FROM purchase_return_lines WHERE return_id = ?",
                BigDecimal.class, goodsReturn);
        LocalDate reverseDate = jdbc.queryForObject("SELECT reverse_date FROM purchase_returns WHERE id = ?",
                LocalDate.class, discount);
        String to = reverseDate.isAfter(LocalDate.parse(DATE)) ? reverseDate.toString() : DATE;

        mockMvc.perform(ledger(buyer, item, DATE, to))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.generatedAt").isNotEmpty())
                .andExpect(jsonPath("$.data.lines.length()").value(4))
                .andExpect(jsonPath("$.data.lines[0].documentType").value("DMH"))
                .andExpect(jsonPath("$.data.lines[0].documentId").value(order))
                .andExpect(jsonPath("$.data.lines[0].invoiceNo").value("88"))
                .andExpect(jsonPath("$.data.lines[0].unitName").isNotEmpty())
                .andExpect(jsonPath("$.data.lines[0].purchaseQuantity").value(3))
                .andExpect(jsonPath("$.data.lines[0].unitPrice").value(500000))
                .andExpect(jsonPath("$.data.lines[0].purchaseAmount").value(1500000))
                .andExpect(jsonPath("$.data.lines[0].discountAmount").value(150000))
                .andExpect(jsonPath(THN + ".returnQuantity").value(contains(1.0)))
                .andExpect(jsonPath(THN + ".returnAmount").value(contains(returned.doubleValue())))
                .andExpect(jsonPath(THN + ".purchaseAmount").value(contains(nullValue())))
                .andExpect(jsonPath(GGM + "[?(@.reversal == false)].priceReductionAmount").value(contains(200000.0)))
                .andExpect(jsonPath(GGM + "[?(@.reversal == true)].priceReductionAmount").value(contains(-200000.0)))
                .andExpect(jsonPath(GGM + "[?(@.reversal == true)].postingDate")
                        .value(contains(reverseDate.toString())))
                .andExpect(jsonPath("$.data.total.lineCount").value(4))
                .andExpect(jsonPath("$.data.total.purchaseQuantity").value(3))
                .andExpect(jsonPath("$.data.total.purchaseAmount").value(1500000))
                .andExpect(jsonPath("$.data.total.discountAmount").value(150000))
                .andExpect(jsonPath("$.data.total.returnQuantity").value(1))
                .andExpect(jsonPath("$.data.total.returnAmount").value(returned.doubleValue()))
                .andExpect(jsonPath("$.data.total.priceReductionAmount").value(0));

        long buyerId = id("SELECT id FROM sys_users WHERE username = 'muahang.hn'");
        long clerkId = id("SELECT id FROM sys_users WHERE username = 'ketoancongno'");
        mockMvc.perform(ledger(buyer, item, DATE, to).param("purchaserId", String.valueOf(buyerId)))
                .andExpect(jsonPath("$.data.lines[0].documentId").value(order))
                .andExpect(jsonPath("$.data.lines.length()").value(1));
        mockMvc.perform(ledger(buyer, item, DATE, to).param("purchaserId", String.valueOf(clerkId)))
                .andExpect(jsonPath("$.data.lines.length()").value(3));
        long category = id("SELECT id FROM product_categories WHERE company_id = 1 AND code = 'VT'");
        mockMvc.perform(ledger(buyer, item, DATE, to).param("productCategoryId", String.valueOf(category)))
                .andExpect(jsonPath("$.data.lines.length()").value(4));
    }

    @Test
    @DisplayName("AT-10, BR-RPT-12: không quyền 403, chưa đăng nhập 401, ngoài phạm vi chi nhánh và điều kiện sai 400")
    void accessAndValidation() throws Exception {
        mockMvc.perform(as(keeper, get(LEDGER)).param("companyId", "1").param("branchId", "1")
                .param("dateFrom", DATE).param("dateTo", DATE)).andExpect(status().isForbidden());
        mockMvc.perform(get(LEDGER).param("companyId", "1").param("branchId", "1").param("dateFrom", DATE)
                .param("dateTo", DATE)).andExpect(status().isUnauthorized());
        mockMvc.perform(as(buyer, get(LEDGER)).param("companyId", "1").param("dateFrom", DATE).param("dateTo", DATE))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").value("branchId"));
        mockMvc.perform(as(buyer, get(LEDGER)).param("companyId", "1").param("branchId", "2")
                        .param("dateFrom", DATE).param("dateTo", DATE))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").value("branchId"));
        mockMvc.perform(as(buyer, get(LEDGER)).param("companyId", "1").param("branchId", "1")
                        .param("dateFrom", "2026-10-31").param("dateTo", "2026-10-01"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").value("dateFrom"));
        mockMvc.perform(as(buyer, get(LEDGER)).param("companyId", "1").param("branchId", "1")
                        .param("dateFrom", DATE).param("dateTo", DATE).param("purchaserId", "999999999"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").value("purchaserId"));
    }

    private MockHttpServletRequestBuilder ledger(Session session, long product, String from, String to) {
        return as(session, get(LEDGER)).param("companyId", "1").param("branchId", "1").param("dateFrom", from)
                .param("dateTo", to).param("productIds", String.valueOf(product));
    }

    /** Hàng mới (đơn vị Cái, không lô) để sổ không lẫn phát sinh của test khác. */
    private long newProduct() throws Exception {
        Session admin = login(mockMvc, "admin");
        String code = unique("P");
        String body = "{\"companyId\":1,\"code\":\"" + code + "\",\"name\":\"Hàng " + code + "\",\"categoryId\":"
                + id("SELECT id FROM product_categories WHERE company_id = 1 AND code = 'VT'")
                + ",\"productType\":\"CONSUMABLE\",\"baseUnitId\":" + unit("CAI")
                + ",\"lotTracked\":false,\"expiryTracked\":false,\"serialTracked\":false}";
        return jsonId(send(admin, post("/api/v1/products"), body, newKey()).andExpect(status().isCreated())
                .andReturn());
    }

    private long postedReturn(String body) throws Exception {
        long document = jsonId(send(clerk, post(RETURNS), body, newKey()).andExpect(status().isCreated())
                .andReturn());
        act(clerk, RETURNS, document, "submit", null).andExpect(jsonPath("$.data.status").value("APPROVED"));
        act(clerk, RETURNS, document, "post", null).andExpect(jsonPath("$.data.status").value("POSTED"));
        return document;
    }

    private static String returnBody(String type, long supplier, long order, String line) {
        return "{\"documentType\":\"" + type + "\",\"branchId\":1,\"supplierId\":" + supplier + ",\"purchaseOrderId\":"
                + order + ",\"documentDate\":\"" + DATE + "\",\"lines\":[" + line + "]}";
    }
}
