package com.company.erp.sales;

import static com.company.erp.support.ApiTestSupport.login;
import static com.company.erp.support.ApiTestSupport.newKey;
import static com.company.erp.support.ApiTestSupport.unique;
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
 * Giai đoạn 6F trên MySQL thật: bán nhanh sinh đơn, phiếu giao, hóa đơn riêng và liên kết, rollback khi thiếu tồn
 * (BR-SAL-42, AT-13 gửi lại); báo cáo doanh số khớp chứng từ cấu thành (BR-SAL-51, AT-12 phần bán hàng); lịch thu tiền
 * (BR-SAL-50).
 */
@MySqlIntegrationTest
@AutoConfigureMockMvc
class QuickSaleReportIntegrationTest extends SalesIntegrationSupport {

    private static final String QUICK = "/api/v1/sales/quick-sale";

    @Autowired
    QuickSaleReportIntegrationTest(MockMvc mockMvc, JdbcTemplate jdbc) {
        super(mockMvc, jdbc);
    }

    @Test
    @DisplayName("BR-SAL-42, AT-13: bán nhanh đủ ba chứng từ; dừng khi phải duyệt; thiếu tồn không lưu gì; gửi lại")
    void quickSale() throws Exception {
        Session shop = login(mockMvc, "banhang.hn");
        long item = newProduct(true, false);
        String lot = unique("L");
        stockIn(item, "5", lot, "2028-12-31", null);
        long clinic = partner("KH_PK01");
        String body = quick(clinic, item, "2", "150000", null, unique("Bán nhanh"));
        String key = newKey();
        MvcResult done = send(shop, post(QUICK), body, key).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.orderStatus").value("COMPLETED"))
                .andExpect(jsonPath("$.data.deliveryNo").isNotEmpty())
                .andExpect(jsonPath("$.data.invoiceNo").isNotEmpty()).andReturn();
        long invoice = ((Number) json(done, "$.data.invoiceId")).longValue();
        long delivery = ((Number) json(done, "$.data.deliveryId")).longValue();
        assertThat(lotOnHand(lot)).isEqualByComparingTo("3");
        assertThat(jdbc.queryForObject("SELECT delivery_status FROM sales_deliveries WHERE id = ?", String.class,
                delivery)).isEqualTo("DELIVERED");
        assertThat(jdbc.queryForObject("SELECT original_amount FROM debt_items WHERE source_type = 'HDB' AND "
                + "source_id = ?", BigDecimal.class, invoice)).isEqualByComparingTo("300000");
        send(shop, post(QUICK), body, key).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.invoiceId").value(invoice));
        assertThat(lotOnHand(lot)).isEqualByComparingTo("3");

        send(shop, post(QUICK), quick(clinic, item, "1", "150000", "30", unique("Bán nhanh")), newKey())
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.orderStatus").value("SUBMITTED"))
                .andExpect(jsonPath("$.data.deliveryId").doesNotExist());
        String missing = unique("Thiếu");
        send(shop, post(QUICK), quick(clinic, item, "100", "150000", null, missing), newKey())
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.code").value("INSUFFICIENT_STOCK"));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sales_orders WHERE description = ?", Long.class, missing))
                .isZero();
        send(seller, post(QUICK), body, newKey()).andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("BR-SAL-51, AT-12, BR-SAL-50: doanh số theo khách khớp tổng chứng từ cấu thành; lịch thu tiền")
    void reportAndSchedule() throws Exception {
        Session shop = login(mockMvc, "banhang.hn");
        long item = newProduct(false, false);
        stockIn(item, "3", null, null, null);
        long clinic = partner("KH_PK01");
        send(shop, post(QUICK), quick(clinic, item, "3", "100000", null, unique("Doanh số")), newKey())
                .andExpect(jsonPath("$.data.orderStatus").value("COMPLETED"));

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
        mockMvc.perform(as(seller, get("/api/v1/sales/reports/summary")).param("companyId", "1")
                .param("dateFrom", "2026-10-01").param("dateTo", "2026-10-31").param("groupBy", "CUSTOMER"))
                .andExpect(status().isForbidden());

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
        send(manager, post(ORDERS), "{\"branchId\":1,\"customerId\":" + clinic + ",\"warehouseId\":"
                + warehouse("KHO_HN") + ",\"documentDate\":\"" + DATE + "\",\"lines\":[" + line(item, unit("CAI"), "1",
                "100000", null) + "],\"paymentTerms\":[{\"percent\":40,\"dueBasis\":\"ORDER_DATE\",\"dueDays\":0}]}",
                newKey()).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("paymentTerms"));
    }

    private String quick(long customer, long item, String quantity, String price, String discount,
                         String description) {
        return "{\"branchId\":1,\"customerId\":" + customer + ",\"warehouseId\":" + warehouse("KHO_HN")
                + ",\"documentDate\":\"" + DATE + "\",\"description\":\"" + description + "\",\"lines\":["
                + line(item, unit("CAI"), quantity, price, discount) + "]}";
    }
}
