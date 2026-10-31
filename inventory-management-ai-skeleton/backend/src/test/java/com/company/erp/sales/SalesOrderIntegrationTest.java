package com.company.erp.sales;

import static com.company.erp.support.ApiTestSupport.login;
import static com.company.erp.support.ApiTestSupport.newKey;
import static com.company.erp.support.ApiTestSupport.unique;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItems;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.company.erp.common.application.IdempotentCommand;
import com.company.erp.support.ApiTestSupport.Session;
import com.company.erp.support.MySqlIntegrationTest;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

/**
 * Đơn bán gộp (D-54) trên MySQL thật: giá ưu tiên và duyệt giá / hạn mức (BR-SAL-02, 03, 14), giữ hàng (BR-SAL-13,
 * D-37), ghi sổ = xuất kho FEFO + doanh thu + công nợ + bút toán (BR-SAL-21, 30, AT-03), hạn dùng tối thiểu (D-07),
 * theo dõi giao hàng và giao thất bại (BR-SAL-22, 23, AT-05), số hóa đơn điện tử chống trùng (BR-SAL-31), phân quyền
 * (AT-10), chống xử lý trùng (AT-13), đảo (AT-09).
 */
@MySqlIntegrationTest
@AutoConfigureMockMvc
class SalesOrderIntegrationTest extends SalesIntegrationSupport {

    @Autowired
    SalesOrderIntegrationTest(MockMvc mockMvc, JdbcTemplate jdbc) {
        super(mockMvc, jdbc);
    }

    @Test
    @DisplayName("BR-SAL-02, 03, 14, D-22, D-61, AT-10: giá ưu tiên; giá dưới sàn / vượt hạn mức chỉ người có quyền vượt ghi sổ; phạm vi khách của nhân viên")
    void pricingAndAuthority() throws Exception {
        long clinic = partner("KH_PK01");
        long agent = partner("KH_DL01");
        long tube = product("VT_ONG_01");
        long box = unit("HOP");
        long store = warehouse("KHO_HN");
        mockMvc.perform(as(seller, get("/api/v1/sales/price")).param("customerId", String.valueOf(agent))
                        .param("productId", String.valueOf(tube)).param("unitId", String.valueOf(box)))
                .andExpect(status().isBadRequest());
        mockMvc.perform(as(manager, get("/api/v1/sales/price")).param("customerId", String.valueOf(agent))
                        .param("productId", String.valueOf(tube)).param("unitId", String.valueOf(box)))
                .andExpect(jsonPath("$.data.unitPrice").value(700000))
                .andExpect(jsonPath("$.data.priceSource").value("CUSTOMER_GROUP"))
                .andExpect(jsonPath("$.data.floorPrice").value(600000))
                .andExpect(jsonPath("$.data.defaultDiscountPercent").value(5));

        long cheap = jsonId(send(seller, post(ORDERS), order(clinic, store, false, List.of(line(tube, box, "1", null,
                "30"))), newKey()).andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.lines[0].unitPrice").value(800000))
                .andExpect(jsonPath("$.data.lines[0].priceSource").value("LIST"))
                .andExpect(jsonPath("$.data.status").value("DRAFT"))
                .andExpect(jsonPath("$.data.approvalReasons").value(containsString("giá sàn"))).andReturn());
        act(seller, ORDERS, cheap, "post", null).andExpect(status().isForbidden());
        act(keeper, ORDERS, cheap, "post", null).andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("PRICE_BELOW_FLOOR"));
        assertThat(jdbc.queryForObject("SELECT status FROM sales_orders WHERE id = ?", String.class, cheap))
                .isEqualTo("DRAFT");
        act(manager, ORDERS, cheap, "post", null).andExpect(jsonPath("$.data.status").value("POSTED"));
        assertThat(id("SELECT COUNT(*) FROM sys_audit_logs WHERE entity_type = 'SALES_ORDER' AND entity_id = " + cheap
                + " AND action = 'POST' AND reason LIKE 'Ghi sổ vượt thẩm quyền%'")).isEqualTo(1);

        long overLimit = jsonId(send(seller, post(ORDERS), order(clinic, store, false, List.of(
                line(tube, box, "70", null, null))), newKey()).andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.totalAmount").value(56000000))
                .andExpect(jsonPath("$.data.approvalReasons").value(containsString("hạn mức"))).andReturn());
        act(keeper, ORDERS, overLimit, "post", null).andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("CREDIT_LIMIT_EXCEEDED"));

        send(seller, post(ORDERS), order(partner("KH_BV01"), store, false, List.of(line(tube, box, "1", null, null))),
                newKey()).andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").value("customerId"));
        long managerOrder = jsonId(send(manager, post(ORDERS), order(partner("KH_BV01"), store, false, List.of(
                line(tube, box, "1", null, null))), newKey()).andExpect(status().isCreated()).andReturn());
        mockMvc.perform(as(seller, get(ORDERS + "/" + managerOrder))).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("BR-SAL-13, D-37, D-61: giữ hàng ngay trên đơn nháp, nhả, giữ lại không cộng dồn, sửa giữ lại theo dòng mới; D-65 xóa đơn nháp nhả hàng; cảnh báo thiếu tồn")
    void reservation() throws Exception {
        long tube = product("VT_ONG_01");
        long box = unit("HOP");
        long store = warehouse("KHO_HN");
        stockIn("10", unique("L"), "2028-06-30");
        BigDecimal before = reserved(tube, store);
        long reserving = jsonId(send(manager, post(ORDERS), order(partner("KH_BV01"), store, true, List.of(
                line(tube, box, "5", null, null))), newKey()).andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("DRAFT")).andReturn());
        mockMvc.perform(as(manager, get(ORDERS + "/" + reserving)))
                .andExpect(jsonPath("$.data.reservedUntil").isNotEmpty())
                .andExpect(jsonPath("$.data.lines[0].reservedQuantity").value(500));
        assertThat(reserved(tube, store).subtract(before)).isEqualByComparingTo("500");
        long version = id("SELECT version FROM sales_orders WHERE id = " + reserving);
        String revised = order(partner("KH_BV01"), store, true, List.of(line(tube, box, "3", null, null)))
                .replaceFirst("}$", ",\"version\":" + version + "}");
        mockMvc.perform(as(manager, put(ORDERS + "/" + reserving)).contentType("application/json").content(revised))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.lines[0].reservedQuantity").value(300));
        assertThat(reserved(tube, store).subtract(before)).isEqualByComparingTo("300");
        act(manager, ORDERS, reserving, "release", null).andExpect(status().isOk());
        assertThat(reserved(tube, store)).isEqualByComparingTo(before);
        act(manager, ORDERS, reserving, "reserve", null).andExpect(jsonPath("$.data.lines[0].reservedQuantity")
                .value(300));
        act(manager, ORDERS, reserving, "reserve", null).andExpect(status().isOk());
        assertThat(reserved(tube, store).subtract(before)).isEqualByComparingTo("300");
        String reason = "{\"reason\":\"Khách đổi kế hoạch\"}";
        send(keeper, delete(ORDERS + "/" + reserving), reason, newKey()).andExpect(status().isForbidden());
        send(manager, delete(ORDERS + "/" + reserving), "{}", newKey()).andExpect(status().isBadRequest());
        String key = newKey();
        send(manager, delete(ORDERS + "/" + reserving), reason, key).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(reserving));
        send(manager, delete(ORDERS + "/" + reserving), reason, key).andExpect(status().isOk());
        assertThat(reserved(tube, store)).isEqualByComparingTo(before);
        assertThat(id("SELECT COUNT(*) FROM sales_orders WHERE id = " + reserving)).isZero();
        assertThat(id("SELECT COUNT(*) FROM sys_audit_logs WHERE entity_type = 'SALES_ORDER' AND entity_id = "
                + reserving + " AND action = 'DELETE' AND before_value IS NOT NULL")).isEqualTo(1);

        send(manager, post(ORDERS), order(partner("KH_BV01"), store, false, List.of(line(tube, box, "100000", null,
                null))), newKey()).andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.availabilityWarnings.length()").value(1));
    }

    @Test
    @DisplayName("AT-03, AT-05, AT-13, BR-SAL-21..23, 30, 31: ghi sổ xuất FEFO dùng hàng đã giữ, doanh thu, công nợ, bút toán; gửi lại cùng key; giao hàng; HĐĐT; giao thất bại đảo đơn; D-07")
    void postDeliverAndFail() throws Exception {
        long item = newProduct(true, false);
        long piece = unit("CAI");
        long store = warehouse("KHO_HN");
        long clinic = partner("KH_PK01");
        long vat = id("SELECT id FROM tax_rates WHERE company_id = 1 AND code = 'VAT10'");
        String later = unique("LA");
        String earlier = unique("LB");
        stockIn(item, "3", later, "2027-03-31", null);
        stockIn(item, "4", earlier, "2027-01-31", null);
        String body = "{\"branchId\":1,\"customerId\":" + clinic + ",\"warehouseId\":" + store + ",\"documentDate\":\""
                + DATE + "\",\"reserveStock\":true,\"lines\":[{\"productId\":" + item + ",\"unitId\":" + piece
                + ",\"quantity\":5,\"unitPrice\":100000,\"taxRateId\":" + vat + "},{\"productId\":"
                + product("DV_VC") + ",\"unitId\":" + piece + ",\"quantity\":1,\"unitPrice\":200000}]}";
        long order = jsonId(send(manager, post(ORDERS), body, newKey()).andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.lines[1].serviceLine").value(true))
                .andExpect(jsonPath("$.data.totalAmount").value(770000)).andReturn());
        assertThat(reserved(item, store)).isEqualByComparingTo("5");

        act(courier, ORDERS, order, "post", null).andExpect(status().isForbidden());
        mockMvc.perform(as(manager, get(ORDERS + "/" + order + "/stock-out-slip"))).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_DOCUMENT_STATE"));
        String key = newKey();
        MvcResult posted = postOrder(order, key).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("POSTED"))
                .andExpect(jsonPath("$.data.deliveryStatus").value("PENDING"))
                .andExpect(jsonPath("$.data.dueDate").value("2026-11-04"))
                .andExpect(jsonPath("$.data.inventoryDocumentNo").isNotEmpty())
                .andExpect(jsonPath("$.data.lines[0].items.length()").value(2)).andReturn();
        postOrder(order, key).andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("POSTED"));
        act(keeper, ORDERS, order, "post", null).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_DOCUMENT_STATE"));
        long inventoryId = ((Number) json(posted, "$.data.inventoryDocumentId")).longValue();
        assertThat(jdbc.queryForMap("SELECT source_type, sub_type FROM inventory_documents WHERE id = ?", inventoryId))
                .containsEntry("source_type", "DBH").containsEntry("sub_type", "SALE");
        assertThat(id("SELECT COUNT(*) FROM inventory_documents WHERE source_type = 'DBH' AND source_id = " + order))
                .isEqualTo(1);
        assertThat(lotOnHand(earlier)).isEqualByComparingTo("0");
        assertThat(lotOnHand(later)).isEqualByComparingTo("2");
        assertThat(reserved(item, store)).isEqualByComparingTo("0");
        Map<String, Object> debt = jdbc.queryForMap("SELECT debt_type, original_amount FROM debt_items WHERE "
                + "source_type = 'DBH' AND source_id = ?", order);
        assertThat(debt).containsEntry("debt_type", "RECEIVABLE");
        assertThat((BigDecimal) debt.get("original_amount")).isEqualByComparingTo("770000");
        assertThat(journal("DBH", order)).containsExactly("N:131:500000", "C:5111:500000", "N:131:200000",
                "C:5113:200000", "N:131:70000", "C:3331:70000");

        act(courier, ORDERS, order, "ship", "{\"shipperName\":\"Xe công ty\"}")
                .andExpect(jsonPath("$.data.deliveryStatus").value("IN_TRANSIT"));
        act(courier, ORDERS, order, "confirm", "{\"receivedDate\":\"" + DATE
                + "\",\"receivedBy\":\"Khoa Dược\",\"signedDocumentRef\":\"BBGN-01\"}")
                .andExpect(jsonPath("$.data.deliveryStatus").value("DELIVERED"))
                .andExpect(jsonPath("$.data.receivedBy").value("Khoa Dược"));
        String series = unique("1C26T");
        String einvoice = "{\"series\":\"" + series + "\",\"number\":\"0000001\",\"date\":\"" + DATE
                + "\",\"taxAuthorityCode\":\"M1-26-ABC\"}";
        act(courier, ORDERS, order, "einvoice-reference", einvoice).andExpect(status().isForbidden());
        act(clerk, ORDERS, order, "einvoice-reference", einvoice)
                .andExpect(jsonPath("$.data.einvoiceNo").value("0000001"));
        long other = jsonId(send(manager, post(ORDERS), order(partner("KH_BV01"), store, false, List.of(
                line(item, piece, "1", "100000", null))), newKey()).andExpect(status().isCreated()).andReturn());
        act(clerk, ORDERS, other, "einvoice-reference", einvoice).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE_DOCUMENT"));

        // Phiếu xuất kho in từ đơn: chỉ dòng hàng hóa, lô thực xuất, giá bán trước thuế, tài khoản phần hàng hóa
        mockMvc.perform(as(manager, get(ORDERS + "/" + order + "/stock-out-slip"))).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.slipNo").value((String) json(posted, "$.data.inventoryDocumentNo")))
                .andExpect(jsonPath("$.data.companyName").isNotEmpty())
                .andExpect(jsonPath("$.data.einvoiceNo").value("0000001"))
                .andExpect(jsonPath("$.data.debitAccounts.length()").value(1))
                .andExpect(jsonPath("$.data.debitAccounts[0]").value("131"))
                .andExpect(jsonPath("$.data.creditAccounts.length()").value(1))
                .andExpect(jsonPath("$.data.creditAccounts[0]").value("5111"))
                .andExpect(jsonPath("$.data.lines.length()").value(1))
                .andExpect(jsonPath("$.data.lines[0].items.length()").value(2))
                .andExpect(jsonPath("$.data.lines[0].unitPrice").value(100000))
                .andExpect(jsonPath("$.data.totalAmount").value(500000))
                .andExpect(jsonPath("$.data.taxRates[0]").value(10))
                .andExpect(jsonPath("$.data.taxAmount").value(50000))
                .andExpect(jsonPath("$.data.paymentAmount").value(550000));

        // D-58: đầu phiếu theo công ty chọn khi in; ngoài phạm vi bị từ chối; in theo công ty khác ghi nhật ký
        String printCompanyCode = unique("CT");
        jdbc.update("INSERT INTO companies (code, name, address, status, created_at, updated_at) "
                + "VALUES (?, 'Công ty in thử', '12 Phố In, Hà Nội', 'ACTIVE', UTC_TIMESTAMP(3), UTC_TIMESTAMP(3))",
                printCompanyCode);
        Long printCompany = jdbc.queryForObject("SELECT id FROM companies WHERE code = ?", Long.class, printCompanyCode);
        mockMvc.perform(as(manager, get(ORDERS + "/" + order + "/stock-out-slip")).param("companyId",
                        String.valueOf(printCompany)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").value("companyId"));
        jdbc.update("INSERT INTO sys_user_data_scopes (user_id, scope_type, scope_id) "
                + "SELECT id, 'COMPANY', ? FROM sys_users WHERE username = 'truongkd.hn'", printCompany);
        try {
            Session scoped = login(mockMvc, "truongkd.hn");
            mockMvc.perform(as(scoped, get(ORDERS + "/print-companies"))).andExpect(status().isOk())
                    .andExpect(jsonPath("$.data[?(@.id == " + printCompany + ")].address").value("12 Phố In, Hà Nội"));
            mockMvc.perform(as(scoped, get(ORDERS + "/" + order + "/stock-out-slip")).param("companyId",
                            String.valueOf(printCompany)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.companyName").value("Công ty in thử"))
                    .andExpect(jsonPath("$.data.companyAddress").value("12 Phố In, Hà Nội"));
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sys_audit_logs WHERE action = 'PRINT_AS_COMPANY' "
                    + "AND entity_type = 'SALES_ORDER' AND entity_id = ?", Integer.class, order)).isEqualTo(1);
        } finally {
            jdbc.update("DELETE FROM sys_user_data_scopes WHERE scope_type = 'COMPANY' AND scope_id = ?", printCompany);
        }

        act(courier, ORDERS, order, "reverse", "{\"reason\":\"Khách từ chối nhận\"}").andExpect(status().isForbidden());
        act(chief, ORDERS, order, "reverse", "{\"reason\":\"Khách từ chối nhận\"}")
                .andExpect(jsonPath("$.data.status").value("REVERSED"));
        mockMvc.perform(as(manager, get(ORDERS + "/" + order + "/stock-out-slip"))).andExpect(status().isConflict());
        assertThat(lotOnHand(earlier)).isEqualByComparingTo("4");
        assertThat(lotOnHand(later)).isEqualByComparingTo("3");
        assertThat(jdbc.queryForObject("SELECT status FROM debt_items WHERE source_type = 'DBH' AND source_id = ?",
                String.class, order)).isEqualTo("REVERSED");
        assertThat(id("SELECT COUNT(*) FROM journal_entries WHERE source_type = 'DBH' AND source_id = " + order
                + " AND reversal_of_id IS NOT NULL")).isEqualTo(1);
        act(clerk, ORDERS, other, "einvoice-reference", einvoice).andExpect(status().isOk());

        long shortLife = newProduct(true, false);
        String expiring = unique("LS");
        stockIn(shortLife, "2", expiring, "2026-11-30", null);
        long blocked = jsonId(send(manager, post(ORDERS), order(clinic, store, false, List.of(
                line(shortLife, piece, "1", "100000", null))), newKey()).andExpect(status().isCreated()).andReturn());
        act(manager, ORDERS, blocked, "post", null).andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("LOT_NOT_SELLABLE"));
        assertThat(lotOnHand(expiring)).isEqualByComparingTo("2");
        assertThat(jdbc.queryForObject("SELECT status FROM sales_orders WHERE id = ?", String.class, blocked))
                .isEqualTo("DRAFT");
    }

    @Test
    @DisplayName("BR-SAL-01, BR-UX-05: đọc dòng hàng đơn bán từ Excel để điền form; giá / lô trống giữ trống; không lưu")
    void importLines() throws Exception {
        mockMvc.perform(as(seller, get(ORDERS + "/line-import-template"))).andExpect(status().isOk());
        long tube = product("VT_ONG_01");
        long vat = jdbc.queryForObject("SELECT id FROM tax_rates WHERE company_id = 1 AND code = 'VAT10'", Long.class);
        long ordersBefore = jdbc.queryForObject("SELECT COUNT(*) FROM sales_orders", Long.class);

        importLines(workbookOf(List.of(
                new String[] {"VT_ONG_01", "", "0"},
                new String[] {"KHONG_CO_MA", "", "1"},
                new String[] {"VT_ONG_01", "", "1", "", "", "", "X_SAI"})))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[*].field").value(
                        hasItems("rows[2].quantity", "rows[3].productId", "rows[4].promotional")));

        importLines(workbookOf(List.of(
                new String[] {"vt_ong_01", "HOP", "2", "", "", "", "", "20/10/2026"},
                new String[] {"VT_ONG_01", "", "1", "150000", "10", "VAT10", "C", "", "LO-CHI-DINH", "Khách yêu cầu"})))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.created").value(2))
                .andExpect(jsonPath("$.data.lines[0].productId").value(tube))
                .andExpect(jsonPath("$.data.lines[0].unitId").value(unit("HOP")))
                .andExpect(jsonPath("$.data.lines[0].unitPrice").doesNotExist())
                .andExpect(jsonPath("$.data.lines[0].lotNo").doesNotExist())
                .andExpect(jsonPath("$.data.lines[0].expectedDate").value("2026-10-20"))
                .andExpect(jsonPath("$.data.lines[1].unitPrice").value(150000))
                .andExpect(jsonPath("$.data.lines[1].taxRateId").value(vat))
                .andExpect(jsonPath("$.data.lines[1].promotional").value(true))
                .andExpect(jsonPath("$.data.lines[1].lotNo").value("LO-CHI-DINH"))
                .andExpect(jsonPath("$.data.lines[1].lotOverrideReason").value("Khách yêu cầu"));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sales_orders", Long.class)).isEqualTo(ordersBefore);
    }

    private ResultActions importLines(byte[] content) throws Exception {
        return mockMvc.perform(as(seller, multipart(ORDERS + "/line-import")
                        .file(new MockMultipartFile("file", "dong-don-ban.xlsx", "application/octet-stream", content)))
                .param("companyId", "1").param("documentDate", DATE));
    }

    private static byte[] workbookOf(List<String[]> rows) throws Exception {
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("Dong don ban");
            sheet.createRow(0).createCell(0).setCellValue("Mã hàng*");
            for (int r = 0; r < rows.size(); r++) {
                Row row = sheet.createRow(r + 1);
                for (int c = 0; c < rows.get(r).length; c++) {
                    row.createCell(c).setCellValue(rows.get(r)[c]);
                }
            }
            workbook.write(output);
            return output.toByteArray();
        }
    }

    /** Ghi sổ bởi Trưởng phòng kinh doanh (có quyền vượt): khách mẫu dùng chung có thể có nợ quá hạn từ test khác. */
    private ResultActions postOrder(long order, String key) throws Exception {
        return mockMvc.perform(as(manager, post(ORDERS + "/" + order + "/post"))
                .header(IdempotentCommand.HEADER, key));
    }
}
