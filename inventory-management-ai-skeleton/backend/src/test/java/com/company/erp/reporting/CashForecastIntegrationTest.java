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
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * Dòng tiền dự kiến đợt 10D (BR-RPT-09, 30, BR-CSH-06) trên MySQL thật: khoản mới vào đúng nguồn, đúng ngày dự kiến;
 * mọi ô bằng tổng khoản cấu thành, số dư cuối lũy kế đúng (AT-12, BR-RPT-11); quyền và phạm vi (AT-10, BR-RPT-12).
 */
@MySqlIntegrationTest
@AutoConfigureMockMvc
class CashForecastIntegrationTest extends ReportIntegrationSupport {

    private static final String FORECAST = "/cash-flow-forecast";
    private static final List<String> SOURCES = List.of("RECEIVABLE", "SALES_ORDER", "PAYABLE", "PURCHASE_ORDER");

    @Autowired
    CashForecastIntegrationTest(MockMvc mockMvc, JdbcTemplate jdbc) {
        super(mockMvc, jdbc);
    }

    @Test
    @DisplayName("BR-CSH-06, BR-RPT-30, AT-12, D-61: dự thu / dự chi theo nguồn (đơn nháp không tính, đơn đã duyệt trước D-61 tính), mọi ô khớp danh sách khoản, số dư lũy kế")
    void forecastMatchesItems() throws Exception {
        long item = newProduct("CONSUMABLE");
        stockIn(item, "10", unique("L"));
        long customer = customer();
        long sold = postedOrder(customer, item, "3");
        long pending = draftOrder(customer, item, "2", ",\"expectedDate\":\"2026-12-01\"");
        long supplier = id("SELECT id FROM partners WHERE company_id = 1 AND code = 'NCC_01'");
        long purchase = draftPurchase(supplier, item, "4", "50000", unique("L"));
        DocumentContext drafts = items(director, null, null, null);
        assertThat(drafts.<List<Object>>read(rowsOf("SALES_ORDER", pending))).isEmpty();
        assertThat(drafts.<List<Object>>read(rowsOf("PURCHASE_ORDER", purchase))).isEmpty();
        markApprovedBeforeD61("sales_orders", pending);
        markApprovedBeforeD61("purchase_orders", purchase);

        DocumentContext all = items(director, null, null, null);
        assertThat(sumOf(all, rowsOf("RECEIVABLE", sold) + ".amount")).isEqualByComparingTo("300000");
        assertThat(sumOf(all, rowsOf("SALES_ORDER", pending) + ".amount")).isEqualByComparingTo("200000");
        assertThat(sumOf(all, rowsOf("PURCHASE_ORDER", purchase) + ".amount")).isEqualByComparingTo("200000");
        int termDays = jdbc.queryForObject("SELECT COALESCE(payment_term_days, 0) FROM sales_orders WHERE id = "
                + pending, Integer.class);
        assertThat(all.<List<String>>read(rowsOf("SALES_ORDER", pending) + ".expectedDate"))
                .containsExactly(LocalDate.of(2026, 12, 1).plusDays(termDays).toString());

        DocumentContext forecast = forecast(director, "MONTH", "3");
        List<Map<String, String>> columns = forecast.read("$.data.columns");
        assertThat(columns).hasSize(5);
        assertThat(columns.get(0).get("key")).isEqualTo("OVERDUE");
        assertThat(columns.get(4).get("to")).isNull();
        BigDecimal everything = BigDecimal.ZERO;
        for (int source = 0; source < SOURCES.size(); source++) {
            assertThat((String) forecast.read("$.data.lines[" + source + "].source")).isEqualTo(SOURCES.get(source));
            for (int column = 0; column < columns.size(); column++) {
                BigDecimal cell = money(forecast, "$.data.lines[" + source + "].amounts[" + column + "]");
                DocumentContext cellItems = items(director, SOURCES.get(source), columns.get(column).get("from"),
                        columns.get(column).get("to"));
                assertThat(money(cellItems, "$.data.total")).as(SOURCES.get(source) + " " + column)
                        .isEqualByComparingTo(cell);
                everything = everything.add(cell);
            }
        }
        assertThat(everything).isEqualByComparingTo(money(all, "$.data.total"));

        BigDecimal opening = money(forecast, "$.data.openingBalance");
        assertThat(sumOf(forecast, "$.data.cashAccounts[*].balance")).isEqualByComparingTo(opening);
        BigDecimal expectedClosing = opening.add(sumOf(forecast, "$.data.inflow[*]"))
                .subtract(sumOf(forecast, "$.data.outflow[*]"));
        assertThat(money(forecast, "$.data.closing[4]")).isEqualByComparingTo(expectedClosing);
        assertThat((Boolean) forecast.read("$.data.cashBalanceIncluded")).isTrue();
        assertThat((String) forecast.read("$.data.generatedAt")).isNotEmpty();
    }

    @Test
    @DisplayName("AT-10, BR-RPT-12: không quyền 403, chưa đăng nhập 401, chi nhánh ngoài phạm vi và điều kiện sai 400")
    void accessAndValidation() throws Exception {
        mockMvc.perform(report(seller, FORECAST)).andExpect(status().isForbidden());
        mockMvc.perform(report(salesManager, FORECAST + "/items")).andExpect(status().isForbidden());
        mockMvc.perform(get(REPORTS + FORECAST).param("companyId", "1")).andExpect(status().isUnauthorized());
        mockMvc.perform(report(cashier, FORECAST).param("branchId", "2"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").value("branchId"));
        mockMvc.perform(report(cashier, FORECAST).param("granularity", "YEAR"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").value("granularity"));
        mockMvc.perform(report(cashier, FORECAST).param("periods", "0"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").value("periods"));
        mockMvc.perform(report(cashier, FORECAST + "/items").param("source", "OTHER"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").value("source"));
        mockMvc.perform(report(cashier, FORECAST))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.columns.length()").value(14));
    }

    private static String rowsOf(String source, long documentId) {
        return "$.data.rows[?(@.source == '" + source + "' && @.documentId == " + documentId + ")]";
    }

    private DocumentContext forecast(Session session, String granularity, String periods) throws Exception {
        return JsonPath.parse(mockMvc.perform(report(session, FORECAST).param("granularity", granularity)
                        .param("periods", periods))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    private DocumentContext items(Session session, String source, String from, String to) throws Exception {
        MockHttpServletRequestBuilder request = report(session, FORECAST + "/items");
        if (source != null) {
            request.param("source", source);
        }
        if (from != null) {
            request.param("dateFrom", from);
        }
        if (to != null) {
            request.param("dateTo", to);
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
}
