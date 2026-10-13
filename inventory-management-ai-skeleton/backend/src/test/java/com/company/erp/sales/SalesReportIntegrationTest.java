package com.company.erp.sales;

import static com.company.erp.support.ApiTestSupport.login;
import static com.company.erp.support.ApiTestSupport.newKey;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.company.erp.support.ApiTestSupport.Session;
import com.company.erp.support.MySqlIntegrationTest;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Báo cáo doanh số khớp chứng từ cấu thành (BR-SAL-51, AT-12) và lịch thu tiền theo đơn (BR-SAL-50) trên MySQL thật;
 * người kiêm nhiệm lập và ghi sổ đơn bán một mình (BR-SAL-42 sau D-54).
 */
@MySqlIntegrationTest
@AutoConfigureMockMvc
class SalesReportIntegrationTest extends SalesIntegrationSupport {

    @Autowired
    SalesReportIntegrationTest(MockMvc mockMvc, JdbcTemplate jdbc) {
        super(mockMvc, jdbc);
    }

    @Test
    @DisplayName("BR-SAL-42, 51, AT-12, AT-10: người kiêm nhiệm lập và ghi sổ đơn; doanh số theo khách khớp tổng chứng từ cấu thành")
    void report() throws Exception {
        Session shop = login(mockMvc, "banhang.hn");
        long item = newProduct(false, false);
        stockIn(item, "3", null, null, null);
        long clinic = partner("KH_PK01");
        long order = jsonId(send(shop, post(ORDERS), order(clinic, warehouse("KHO_HN"), false, List.of(
                line(item, unit("CAI"), "3", "100000", null))), newKey()).andExpect(status().isCreated()).andReturn());
        String submitted = json(act(shop, ORDERS, order, "submit", null).andExpect(status().isOk()).andReturn(),
                "$.data.status");
        if ("SUBMITTED".equals(submitted)) {
            act(manager, ORDERS, order, "approve", "{}").andExpect(jsonPath("$.data.status").value("APPROVED"));
        }
        act(shop, ORDERS, order, "post", null).andExpect(jsonPath("$.data.status").value("POSTED"));

        MvcResult summary = mockMvc.perform(as(manager, get("/api/v1/sales/reports/summary")).param("companyId", "1")
                        .param("branchId", "1").param("dateFrom", "2026-10-01").param("dateTo", "2026-10-31")
                        .param("groupBy", "CUSTOMER")).andExpect(status().isOk()).andReturn();
        List<Number> net = json(summary, "$.data.rows[?(@.key == " + clinic + ")].netRevenue");
        assertThat(new BigDecimal(net.get(0).toString())).isGreaterThanOrEqualTo(new BigDecimal("300000"));
        MvcResult sources = mockMvc.perform(as(manager, get("/api/v1/sales/reports/sources")).param("companyId", "1")
                        .param("branchId", "1").param("dateFrom", "2026-10-01").param("dateTo", "2026-10-31")
                        .param("groupBy", "CUSTOMER").param("key", String.valueOf(clinic)))
                .andExpect(status().isOk()).andReturn();
        List<Number> amounts = json(sources, "$.data[*].amount");
        BigDecimal total = amounts.stream().map(value -> new BigDecimal(value.toString()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(total).isEqualByComparingTo(new BigDecimal(net.get(0).toString()));
        List<String> types = json(sources, "$.data[?(@.documentId == " + order + ")].documentType");
        assertThat(types).containsExactly("DBH");
        mockMvc.perform(as(seller, get("/api/v1/sales/reports/summary")).param("companyId", "1")
                .param("dateFrom", "2026-10-01").param("dateTo", "2026-10-31").param("groupBy", "CUSTOMER"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("BR-SAL-50: lịch thu tiền nhiều đợt; hạn theo ngày đơn và ngày khách nhận; tổng tỷ lệ phải 100%")
    void paymentSchedule() throws Exception {
        long item = newProduct(false, false);
        stockIn(item, "1", null, null, null);
        long clinic = partner("KH_PK01");
        long order = jsonId(send(manager, post(ORDERS), "{\"branchId\":1,\"customerId\":" + clinic + ",\"warehouseId\":"
                + warehouse("KHO_HN") + ",\"documentDate\":\"" + DATE + "\",\"lines\":[" + line(item, unit("CAI"), "1",
                "100001", null) + "],\"paymentTerms\":[{\"percent\":50,\"dueBasis\":\"ORDER_DATE\",\"dueDays\":0},"
                + "{\"percent\":50,\"dueBasis\":\"DELIVERY\",\"dueDays\":30}]}", newKey())
                .andExpect(status().isCreated()).andReturn());
        mockMvc.perform(as(manager, get(ORDERS + "/" + order)))
                .andExpect(jsonPath("$.data.paymentSchedule[0].amount").value(50001))
                .andExpect(jsonPath("$.data.paymentSchedule[1].amount").value(50000))
                .andExpect(jsonPath("$.data.paymentSchedule[0].dueDate").value(DATE))
                .andExpect(jsonPath("$.data.paymentSchedule[1].dueDate").doesNotExist());
        submitAndApproveOrder(order);
        act(keeper, ORDERS, order, "post", null).andExpect(jsonPath("$.data.status").value("POSTED"));
        act(courier, ORDERS, order, "confirm", "{\"receivedDate\":\"2026-10-06\"}")
                .andExpect(jsonPath("$.data.paymentSchedule[1].dueDate").value("2026-11-05"));
        send(manager, post(ORDERS), "{\"branchId\":1,\"customerId\":" + clinic + ",\"warehouseId\":"
                + warehouse("KHO_HN") + ",\"documentDate\":\"" + DATE + "\",\"lines\":[" + line(item, unit("CAI"), "1",
                "100000", null) + "],\"paymentTerms\":[{\"percent\":40,\"dueBasis\":\"ORDER_DATE\",\"dueDays\":0}]}",
                newKey()).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("paymentTerms"));
    }
}
