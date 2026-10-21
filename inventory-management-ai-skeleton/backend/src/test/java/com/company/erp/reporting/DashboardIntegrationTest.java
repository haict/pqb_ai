package com.company.erp.reporting;

import static com.company.erp.support.ApiTestSupport.unique;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.company.erp.support.ApiTestSupport.Session;
import com.company.erp.support.MySqlIntegrationTest;
import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * Dashboard điều hành đợt 10C (BR-RPT-01, 02, 04..06, 10..13) trên MySQL thật: chỉ tiêu tăng đúng theo chứng từ vừa ghi
 * sổ, tổng chi tiết bằng số trên dashboard (AT-12, BR-RPT-11); quyền và phạm vi (AT-10, BR-RPT-12).
 */
@MySqlIntegrationTest
@AutoConfigureMockMvc
class DashboardIntegrationTest extends ReportIntegrationSupport {

    private static final String DASHBOARD = "/api/v1/dashboard";

    @Autowired
    DashboardIntegrationTest(MockMvc mockMvc, JdbcTemplate jdbc) {
        super(mockMvc, jdbc);
    }

    @Test
    @DisplayName("BR-RPT-01, 10, AT-12: doanh thu, giá trị mua tăng đúng theo chứng từ; top và chi tiết khớp chỉ tiêu")
    void tradeMetrics() throws Exception {
        DocumentContext before = summary(director);
        long item = newProduct("CONSUMABLE");
        String lot = unique("L");
        stockIn(item, "10", lot);
        long customer = customer();
        long sold = postedOrder(customer, item, "4");
        postedReturn(customer, sold, lot, "1");
        long supplier = id("SELECT id FROM partners WHERE company_id = 1 AND code = 'NCC_01'");
        postedPurchase(supplier, item, "2", "450000", unique("L"));
        DocumentContext after = summary(director);

        assertThat(delta(before, after, "$.data.revenue.grossAmount")).isEqualByComparingTo("400000");
        assertThat(delta(before, after, "$.data.revenue.deductionAmount")).isEqualByComparingTo("100000");
        assertThat(delta(before, after, "$.data.revenue.netAmount")).isEqualByComparingTo("300000");
        assertThat(delta(before, after, "$.data.purchases.netAmount")).isEqualByComparingTo("900000");
        assertThat((Boolean) after.read("$.data.revenue.costAvailable")).isFalse();
        assertThat((String) after.read("$.data.generatedAt")).isNotEmpty();

        DocumentContext revenue = details(director, "/revenue/details", true, null);
        assertThat(money(revenue, "$.data.total")).isEqualByComparingTo(money(after, "$.data.revenue.netAmount"));
        assertThat(sumOf(revenue, "$.data.rows[*].amount")).isEqualByComparingTo(money(revenue, "$.data.total"));
        DocumentContext purchases = details(director, "/purchases/details", true, null);
        assertThat(money(purchases, "$.data.total")).isEqualByComparingTo(money(after, "$.data.purchases.netAmount"));

        DocumentContext product = details(director, "/revenue/details", true, "productId=" + item);
        assertThat(money(product, "$.data.total")).isEqualByComparingTo("300000");
        assertThat(product.<List<String>>read("$.data.rows[*].documentType")).containsExactly("DBH", "TLB");

        List<Integer> topCustomers = after.read("$.data.topCustomers[*].id");
        assertThat(topCustomers).isNotEmpty();
        DocumentContext topCustomer = details(director, "/revenue/details", true, "partnerId=" + topCustomers.get(0));
        assertThat(money(topCustomer, "$.data.total")).isEqualByComparingTo(money(after, "$.data.topCustomers[0].amount"));
        List<Number> productAmounts = after.read("$.data.topProducts[*].amount");
        assertThat(productAmounts).hasSizeLessThanOrEqualTo(10);
        assertThat(productAmounts.stream().map(amount -> new BigDecimal(amount.toString())).toList())
                .isSortedAccordingTo((left, right) -> right.compareTo(left));
    }

    @Test
    @DisplayName("BR-RPT-02, 04..06, 11: công nợ, hàng sắp hết hạn, đơn chưa giao đủ khớp danh sách chi tiết")
    void statusMetrics() throws Exception {
        long item = newProduct("CONSUMABLE");
        stockIn(item, "10", unique("L"));
        long nearItem = newProduct("CONSUMABLE");
        String nearLot = unique("L");
        stockIn(nearItem, "7", nearLot, LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh")).plusDays(20).toString());
        long customer = customer();
        long sold = postedOrder(customer, item, "3");
        long pending = draftOrder(customer, item, "2", "");
        DocumentContext summary = summary(director);

        DocumentContext receivables = details(director, "/receivables/details", false, null);
        assertThat(money(receivables, "$.data.totalAmount"))
                .isEqualByComparingTo(money(summary, "$.data.receivables.totalAmount"));
        assertThat(money(receivables, "$.data.overdueAmount"))
                .isEqualByComparingTo(money(summary, "$.data.receivables.overdueAmount"));
        assertThat(receivables.<List<Object>>read("$.data.rows"))
                .hasSize(summary.<Integer>read("$.data.receivables.itemCount"));
        assertThat(sumOf(receivables, "$.data.rows[?(@.sourceType == 'DBH' && @.sourceId == " + sold
                + ")].remainingAmount")).isEqualByComparingTo("300000");
        assertThat(summary.<List<Object>>read("$.data.receivables.overdueBuckets")).hasSize(4);
        assertThat(sumOf(summary, "$.data.receivables.overdueBuckets[*].amount"))
                .isEqualByComparingTo(money(summary, "$.data.receivables.overdueAmount"));
        DocumentContext payables = details(director, "/payables/details", false, null);
        assertThat(money(payables, "$.data.totalAmount"))
                .isEqualByComparingTo(money(summary, "$.data.payables.totalAmount"));

        DocumentContext expiring = details(director, "/expiring/details", false, null);
        int expired = summary.read("$.data.expiry.expiredCount");
        int soon = summary.read("$.data.expiry.expiringCount");
        assertThat(expiring.<List<Object>>read("$.data.rows")).hasSize(expired + soon);
        assertThat(sumOf(expiring, "$.data.rows[?(@.lotNo == '" + nearLot + "')].onHand"))
                .isEqualByComparingTo("7");

        DocumentContext sales = details(director, "/sales-backlog/details", false, null);
        assertThat(money(sales, "$.data.totalAmount"))
                .isEqualByComparingTo(money(summary, "$.data.salesBacklog.totalAmount"));
        assertThat(sales.<List<Integer>>read("$.data.rows[*].documentId")).contains((int) pending, (int) sold)
                .hasSize(summary.<Integer>read("$.data.salesBacklog.orderCount"));
        DocumentContext purchases = details(director, "/purchase-backlog/details", false, null);
        assertThat(money(purchases, "$.data.totalAmount"))
                .isEqualByComparingTo(money(summary, "$.data.purchaseBacklog.totalAmount"));
    }

    @Test
    @DisplayName("AT-10, BR-RPT-12: không quyền 403, chưa đăng nhập 401, chi nhánh ngoài phạm vi và điều kiện sai 400")
    void accessAndValidation() throws Exception {
        mockMvc.perform(dashboard(seller, "/summary").param("dateFrom", DATE).param("dateTo", DATE))
                .andExpect(status().isForbidden());
        mockMvc.perform(dashboard(keeper, "/expiring/details")).andExpect(status().isForbidden());
        mockMvc.perform(get(DASHBOARD + "/summary").param("companyId", "1").param("dateFrom", DATE)
                        .param("dateTo", DATE))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(dashboard(salesManager, "/summary").param("branchId", "2").param("dateFrom", DATE)
                        .param("dateTo", DATE))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").value("branchId"));
        mockMvc.perform(dashboard(salesManager, "/receivables/details").param("branchId", "2"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").value("branchId"));
        mockMvc.perform(dashboard(salesManager, "/summary").param("dateFrom", DATE).param("dateTo", DATE)
                        .param("top", "0"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").value("top"));
        mockMvc.perform(dashboard(director, "/revenue/details").param("dateFrom", "2026-10-31").param("dateTo", DATE))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").value("dateFrom"));
        mockMvc.perform(dashboard(salesManager, "/summary").param("dateFrom", DATE).param("dateTo", DATE))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.today").isNotEmpty());
    }

    private MockHttpServletRequestBuilder dashboard(Session session, String path) {
        return as(session, get(DASHBOARD + path)).param("companyId", "1");
    }

    private DocumentContext summary(Session session) throws Exception {
        return JsonPath.parse(mockMvc.perform(dashboard(session, "/summary").param("dateFrom", DATE)
                        .param("dateTo", DATE))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    /** {@code filter} dạng {@code ten=giatri}. */
    private DocumentContext details(Session session, String path, boolean period, String filter) throws Exception {
        MockHttpServletRequestBuilder request = dashboard(session, path);
        if (period) {
            request.param("dateFrom", DATE).param("dateTo", DATE);
        }
        if (filter != null) {
            String[] pair = filter.split("=");
            request.param(pair[0], pair[1]);
        }
        return JsonPath.parse(mockMvc.perform(request).andExpect(status().isOk()).andReturn().getResponse()
                .getContentAsString());
    }

    private static BigDecimal money(DocumentContext json, String path) {
        Object value = json.read(path);
        return new BigDecimal(value.toString());
    }

    private static BigDecimal sumOf(DocumentContext json, String path) {
        List<Object> values = json.read(path);
        return values.stream().map(value -> new BigDecimal(value.toString())).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static BigDecimal delta(DocumentContext before, DocumentContext after, String path) {
        return money(after, path).subtract(money(before, path));
    }
}
