package com.company.erp.reporting;

import static com.company.erp.support.ApiTestSupport.unique;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.company.erp.support.MySqlIntegrationTest;
import com.jayway.jsonpath.JsonPath;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Báo cáo kho M10 (BR-RPT-21..24, ADR-18) trên MySQL thật: thẻ kho và nhập - xuất - tồn khớp số dư (AT-12), đảo trừ
 * đúng chiều (AT-09), truy xuất lô → khách, hàng ngoại lệ, thiếu hồ sơ, tồn lâu; quyền và phạm vi kho (AT-10).
 */
@MySqlIntegrationTest
@AutoConfigureMockMvc
class InventoryReportIntegrationTest extends ReportIntegrationSupport {

    @Autowired
    InventoryReportIntegrationTest(MockMvc mockMvc, JdbcTemplate jdbc) {
        super(mockMvc, jdbc);
    }

    @Test
    @DisplayName("BR-RPT-21, 22, 24, AT-12, AT-09: thẻ kho, nhập - xuất - tồn khớp số dư; đảo trừ đúng chiều; lô → khách")
    void ledgerAndTraceability() throws Exception {
        long item = newProduct("CONSUMABLE");
        String lot = unique("L");
        stockIn(item, "10", lot);
        long lotId = id("SELECT id FROM inventory_lots WHERE lot_no = '" + lot + "'");
        long customer = customer();
        long order = postedOrder(customer, item, "4");
        postedReturn(customer, order, lot, "1");
        long cancelled = postedOrder(customer, item, "2");
        send(chief, post(ORDERS + "/" + cancelled + "/reverse"), "{\"reason\":\"Khách từ chối nhận\"}")
                .andExpect(jsonPath("$.data.status").value("REVERSED"));
        LocalDate tomorrow = LocalDate.now().plusDays(1);
        String to = tomorrow.isAfter(LocalDate.parse(DATE)) ? tomorrow.toString() : DATE;
        BigDecimal balance = jdbc.queryForObject("SELECT SUM(on_hand_quantity) FROM inventory_balances "
                + "WHERE product_id = ?", BigDecimal.class, item);
        assertThat(balance).isEqualByComparingTo("7");

        mockMvc.perform(report(keeper, "/stock-card").param("productId", String.valueOf(item))
                        .param("dateFrom", DATE).param("dateTo", to))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.opening").value(0))
                .andExpect(jsonPath("$.data.totalIn").value(13))
                .andExpect(jsonPath("$.data.totalOut").value(6))
                .andExpect(jsonPath("$.data.closing").value(7))
                .andExpect(jsonPath("$.data.lines.length()").value(5))
                .andExpect(jsonPath("$.data.lines[4].balance").value(7))
                .andExpect(jsonPath("$.data.lines[?(@.reversal == true)].inQuantity").value(hasItem(2.0)))
                .andExpect(jsonPath("$.data.generatedAt").isNotEmpty());
        mockMvc.perform(report(keeper, "/inventory-movement").param("productIds", String.valueOf(item))
                        .param("dateFrom", DATE).param("dateTo", to))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.rows.length()").value(1))
                .andExpect(jsonPath("$.data.rows[0].opening").value(0))
                .andExpect(jsonPath("$.data.rows[0].inQuantity").value(11))
                .andExpect(jsonPath("$.data.rows[0].outQuantity").value(4))
                .andExpect(jsonPath("$.data.rows[0].closing").value(7))
                .andExpect(jsonPath("$.data.valueAvailable").value(false));

        mockMvc.perform(report(keeper, "/lot-trace").param("lotId", String.valueOf(lotId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.lotNo").value(lot))
                .andExpect(jsonPath("$.data.onHand").value(7))
                .andExpect(jsonPath("$.data.customers.length()").value(1))
                .andExpect(jsonPath("$.data.customers[0].partnerId").value(customer))
                .andExpect(jsonPath("$.data.customers[0].issued").value(4))
                .andExpect(jsonPath("$.data.customers[0].returned").value(1))
                .andExpect(jsonPath("$.data.customers[0].net").value(3));
        MvcResult trace = mockMvc.perform(report(keeper, "/customer-trace").param("customerId", String.valueOf(customer))
                        .param("dateFrom", DATE).param("dateTo", to))
                .andExpect(status().isOk()).andReturn();
        List<Number> quantities = JsonPath.read(trace.getResponse().getContentAsString(),
                "$.data.lines[?(@.lotNo == '" + lot + "')].quantity");
        assertThat(quantities.stream().map(value -> new BigDecimal(value.toString())).toList())
                .usingElementComparator(BigDecimal::compareTo)
                .containsExactlyInAnyOrder(new BigDecimal("-4"), BigDecimal.ONE, new BigDecimal("-2"), new BigDecimal("2"));

        mockMvc.perform(report(hcmKeeper, "/lot-trace").param("lotId", String.valueOf(lotId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.lines.length()").value(0))
                .andExpect(jsonPath("$.data.onHand").value(0));
    }

    @Test
    @DisplayName("BR-RPT-23: thuốc thử nhập ở trạng thái chờ kiểm vào ngoại lệ, thiếu số lưu hành; tồn lâu theo số ngày")
    void exceptionsAndMissingDocuments() throws Exception {
        long reagent = newProduct("REAGENT");
        stockIn(reagent, "5", unique("L"));
        String pending = "$.data.rows[?(@.productId == " + reagent + ")]";

        mockMvc.perform(report(keeper, "/stock-exceptions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath(pending + ".onHand").value(hasItem(5.0)))
                .andExpect(jsonPath(pending + ".qualityStatus").value(hasItem("PENDING_INSPECTION")))
                .andExpect(jsonPath(pending + ".reasons[0]").value(hasItem("QUALITY")));
        mockMvc.perform(report(keeper, "/missing-documents"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.rows[?(@.productId == " + reagent + ")].documentTypeCode")
                        .value(hasItem("SO_LH")));

        MvcResult slow = mockMvc.perform(report(keeper, "/slow-moving").param("days", "1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.thresholdDays").value(1)).andReturn();
        String cutoff = JsonPath.read(slow.getResponse().getContentAsString(), "$.data.cutoffDate");
        List<Object> listed = JsonPath.read(slow.getResponse().getContentAsString(),
                "$.data.rows[?(@.productId == " + reagent + ")]");
        assertThat(listed).hasSize(LocalDate.parse(cutoff).isAfter(LocalDate.parse(DATE)) ? 1 : 0);
        mockMvc.perform(report(keeper, "/slow-moving"))
                .andExpect(jsonPath("$.data.thresholdDays").value(180));
    }

    @Test
    @DisplayName("AT-10, BR-RPT-12: không quyền 403, chưa đăng nhập 401, kho ngoài phạm vi 400, điều kiện sai 400")
    void accessAndValidation() throws Exception {
        long product = id("SELECT id FROM products WHERE company_id = 1 AND code = 'VT_ONG_01'");
        long hanoi = id("SELECT id FROM warehouses WHERE company_id = 1 AND code = 'KHO_HN'");
        mockMvc.perform(report(salesManager, "/stock-card").param("productId", String.valueOf(product))
                .param("dateFrom", DATE).param("dateTo", DATE)).andExpect(status().isForbidden());
        mockMvc.perform(get(REPORTS + "/stock-exceptions").param("companyId", "1"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(report(hcmKeeper, "/stock-card").param("productId", String.valueOf(product))
                        .param("warehouseId", String.valueOf(hanoi)).param("dateFrom", DATE).param("dateTo", DATE))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").value("warehouseId"));
        mockMvc.perform(report(keeper, "/stock-card").param("dateFrom", DATE).param("dateTo", DATE))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").value("productId"));
        mockMvc.perform(report(keeper, "/inventory-movement").param("dateFrom", "2026-10-31")
                        .param("dateTo", "2026-10-01"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").value("dateFrom"));
        mockMvc.perform(report(keeper, "/lot-trace"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").value("lotId"));
    }
}
