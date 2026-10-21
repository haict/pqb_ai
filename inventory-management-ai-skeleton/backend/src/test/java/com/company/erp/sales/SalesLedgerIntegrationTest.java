package com.company.erp.sales;

import static com.company.erp.support.ApiTestSupport.newKey;
import static com.company.erp.support.ApiTestSupport.unique;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.company.erp.support.MySqlIntegrationTest;
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
 * Sổ chi tiết bán hàng S16-DNN (BR-RPT-26, H-04) trên MySQL thật: dòng đơn bán, trả lại, đảo; TK đối ứng từ bút toán;
 * cộng phát sinh và doanh thu thuần khớp chứng từ (AT-12); quyền và phạm vi chi nhánh (AT-10).
 */
@MySqlIntegrationTest
@AutoConfigureMockMvc
class SalesLedgerIntegrationTest extends SalesIntegrationSupport {

    private static final String LEDGER = "/api/v1/sales/reports/detail-ledger";
    private static final String RETURNS = "/api/v1/sales-returns";

    @Autowired
    SalesLedgerIntegrationTest(MockMvc mockMvc, JdbcTemplate jdbc) {
        super(mockMvc, jdbc);
    }

    @Test
    @DisplayName("BR-RPT-26, AT-12: đơn bán vào doanh thu, trả lại vào giảm trừ Khác; TK đối ứng 131; doanh thu thuần khớp")
    void saleAndReturn() throws Exception {
        long item = newProduct(true, false);
        String lot = unique("L");
        stockIn(item, "10", lot, "2028-12-31", null);
        long clinic = partner("KH_PK01");
        long vat = id("SELECT id FROM tax_rates WHERE company_id = 1 AND code = 'VAT10'");
        long order = postedOrder(manager, "{\"branchId\":1,\"customerId\":" + clinic + ",\"warehouseId\":"
                + warehouse("KHO_HN") + ",\"documentDate\":\"" + DATE + "\",\"lines\":[{\"productId\":" + item
                + ",\"unitId\":" + unit("CAI") + ",\"quantity\":5,\"unitPrice\":100000,\"taxRateId\":" + vat + "}]}");
        long orderLine = id("SELECT id FROM sales_order_lines WHERE order_id = " + order);
        long goodsReturn = jsonId(send(clerk, post(RETURNS), "{\"documentType\":\"TLB\",\"branchId\":1,"
                + "\"customerId\":" + clinic + ",\"salesOrderId\":" + order + ",\"documentDate\":\"" + DATE
                + "\",\"lines\":[{\"orderLineId\":" + orderLine + ",\"lotNo\":\"" + lot + "\",\"quantity\":2}]}",
                newKey()).andExpect(status().isCreated()).andReturn());
        act(clerk, RETURNS, goodsReturn, "post", null).andExpect(jsonPath("$.data.status").value("POSTED"));

        String block = "$.data.groups[0].products[0]";
        mockMvc.perform(ledger(item, DATE, DATE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.groupBy").value("NONE"))
                .andExpect(jsonPath("$.data.costAvailable").value(false))
                .andExpect(jsonPath("$.data.generatedAt").isNotEmpty())
                .andExpect(jsonPath("$.data.groups.length()").value(1))
                .andExpect(jsonPath(block + ".productId").value(item))
                .andExpect(jsonPath(block + ".lines.length()").value(2))
                .andExpect(jsonPath(block + ".lines[0].documentType").value("DBH"))
                .andExpect(jsonPath(block + ".lines[0].documentId").value(order))
                .andExpect(jsonPath(block + ".lines[0].offsetAccount").value("131"))
                .andExpect(jsonPath(block + ".lines[0].unitName").isNotEmpty())
                .andExpect(jsonPath(block + ".lines[0].quantity").value(5))
                .andExpect(jsonPath(block + ".lines[0].unitPrice").value(100000))
                .andExpect(jsonPath(block + ".lines[0].amount").value(500000))
                .andExpect(jsonPath(block + ".lines[0].otherDeduction").doesNotExist())
                .andExpect(jsonPath(block + ".lines[1].documentType").value("TLB"))
                .andExpect(jsonPath(block + ".lines[1].documentId").value(goodsReturn))
                .andExpect(jsonPath(block + ".lines[1].offsetAccount").value("131"))
                .andExpect(jsonPath(block + ".lines[1].otherDeduction").value(200000))
                .andExpect(jsonPath(block + ".lines[1].amount").doesNotExist())
                .andExpect(jsonPath(block + ".totals.quantity").value(5))
                .andExpect(jsonPath(block + ".totals.amount").value(500000))
                .andExpect(jsonPath(block + ".totals.otherDeduction").value(200000))
                .andExpect(jsonPath(block + ".totals.netRevenue").value(300000))
                .andExpect(jsonPath(block + ".totals.costOfGoodsSold").doesNotExist())
                .andExpect(jsonPath(block + ".totals.grossProfit").doesNotExist())
                .andExpect(jsonPath("$.data.total.netRevenue").value(300000));

        mockMvc.perform(ledger(item, DATE, DATE).param("groupBy", "CUSTOMER"))
                .andExpect(jsonPath("$.data.groups[0].key").value(clinic))
                .andExpect(jsonPath("$.data.groups[0].code").value("KH_PK01"))
                .andExpect(jsonPath("$.data.groups[0].totals.netRevenue").value(300000));
        long category = id("SELECT id FROM product_categories WHERE company_id = 1 AND code = 'VT'");
        mockMvc.perform(ledger(item, DATE, DATE).param("groupBy", "PRODUCT_CATEGORY")
                        .param("productCategoryId", String.valueOf(category)))
                .andExpect(jsonPath("$.data.groups[0].key").value(category))
                .andExpect(jsonPath("$.data.groups[0].products[0].lines.length()").value(2));
    }

    @Test
    @DisplayName("BR-RPT-26, AT-09: đơn bán đã đảo có dòng gốc theo ngày hạch toán và dòng âm theo ngày đảo")
    void reversedOrder() throws Exception {
        long item = newProduct(false, false);
        stockIn(item, "2", null, null, null);
        long order = postedOrder(manager, order(partner("KH_PK01"), warehouse("KHO_HN"), false,
                List.of(line(item, unit("CAI"), "2", "150000", null))));
        act(chief, ORDERS, order, "reverse", "{\"reason\":\"Khách từ chối nhận\"}")
                .andExpect(jsonPath("$.data.status").value("REVERSED"));
        LocalDate reverseDate = jdbc.queryForObject("SELECT reverse_date FROM sales_orders WHERE id = ?",
                LocalDate.class, order);
        String to = reverseDate.isAfter(LocalDate.parse(DATE)) ? reverseDate.toString() : DATE;

        String block = "$.data.groups[0].products[0]";
        mockMvc.perform(ledger(item, DATE, to))
                .andExpect(status().isOk())
                .andExpect(jsonPath(block + ".lines.length()").value(2))
                .andExpect(jsonPath(block + ".lines[0].reversal").value(false))
                .andExpect(jsonPath(block + ".lines[0].amount").value(300000))
                .andExpect(jsonPath(block + ".lines[1].reversal").value(true))
                .andExpect(jsonPath(block + ".lines[1].postingDate").value(reverseDate.toString()))
                .andExpect(jsonPath(block + ".lines[1].quantity").value(-2))
                .andExpect(jsonPath(block + ".lines[1].amount").value(-300000))
                .andExpect(jsonPath(block + ".lines[1].offsetAccount").value("131"))
                .andExpect(jsonPath(block + ".totals.amount").value(0))
                .andExpect(jsonPath(block + ".totals.netRevenue").value(0));
        assertThat(reverseDate).isNotNull();
    }

    @Test
    @DisplayName("AT-10, BR-RPT-12: không quyền 403, chưa đăng nhập 401, ngoài phạm vi chi nhánh và điều kiện sai 400")
    void accessAndValidation() throws Exception {
        mockMvc.perform(as(seller, get(LEDGER)).param("companyId", "1").param("branchId", "1")
                .param("dateFrom", DATE).param("dateTo", DATE)).andExpect(status().isForbidden());
        mockMvc.perform(get(LEDGER).param("companyId", "1").param("branchId", "1").param("dateFrom", DATE)
                .param("dateTo", DATE)).andExpect(status().isUnauthorized());
        mockMvc.perform(as(manager, get(LEDGER)).param("companyId", "1").param("dateFrom", DATE)
                        .param("dateTo", DATE))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").value("branchId"));
        mockMvc.perform(as(manager, get(LEDGER)).param("companyId", "1").param("branchId", "2")
                        .param("dateFrom", DATE).param("dateTo", DATE))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").value("branchId"));
        mockMvc.perform(as(manager, get(LEDGER)).param("companyId", "1").param("branchId", "1")
                        .param("dateFrom", "2026-10-31").param("dateTo", "2026-10-01"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").value("dateFrom"));
        mockMvc.perform(as(manager, get(LEDGER)).param("companyId", "1").param("branchId", "1")
                        .param("dateFrom", DATE).param("dateTo", DATE).param("productCategoryId", "999999999"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("productCategoryId"));
    }

    private MockHttpServletRequestBuilder ledger(long product, String from, String to) {
        return as(manager, get(LEDGER)).param("companyId", "1").param("branchId", "1").param("dateFrom", from)
                .param("dateTo", to).param("productIds", String.valueOf(product));
    }
}
