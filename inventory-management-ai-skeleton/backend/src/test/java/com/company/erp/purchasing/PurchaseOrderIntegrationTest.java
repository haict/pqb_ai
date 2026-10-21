package com.company.erp.purchasing;

import static com.company.erp.support.ApiTestSupport.login;
import static com.company.erp.support.ApiTestSupport.newKey;
import static com.company.erp.support.ApiTestSupport.unique;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItems;
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
 * Đơn mua gộp (D-54) trên MySQL thật: ghi sổ = nhập kho theo lô / serial + công nợ phải trả + bút toán (BR-PUR-04, 08,
 * 09), hàng đang về (BR mục 5.4), hóa đơn trùng (BR-PUR-09), ghi sổ theo ngưỡng thẩm quyền (D-61), phân quyền (AT-10), chống xử lý
 * trùng (AT-13), đảo (AT-09).
 */
@MySqlIntegrationTest
@AutoConfigureMockMvc
class PurchaseOrderIntegrationTest extends PurchasingIntegrationSupport {

    @Autowired
    PurchaseOrderIntegrationTest(MockMvc mockMvc, JdbcTemplate jdbc) {
        super(mockMvc, jdbc);
    }

    @Test
    @DisplayName("AT-01, AT-02, AT-13, BR-PUR-08, 09: ghi sổ đơn mua nhập kho theo lô / serial, công nợ, bút toán; gửi lại cùng key; hóa đơn trùng; đảo")
    void postAndReverse() throws Exception {
        long tube = product("VT_ONG_01");
        long machine = product("MAY_XN_01");
        long service = product("DV_VC");
        long warehouse = warehouse("KHO_HN");
        long supplier = partner("NCC_01");
        long vat = taxRate("VAT10");
        BigDecimal tubeIncoming = incoming(tube, warehouse);
        BigDecimal machineIncoming = incoming(machine, warehouse);
        BigDecimal tubeOnHand = onHand(tube, warehouse);
        String lot = unique("L");
        String serial = unique("SN");
        String series = unique("C26T");
        String body = orderWithInvoice(supplier, warehouse, series, "123", List.of(
                lotLine(tube, unit("HOP"), "2", "500000", vat, lot),
                orderLine(machine, unit("CAI"), "1", "10000000", null, vat).replaceFirst("}$",
                        ",\"serialNumbers\":[\"" + serial + "\"]}"),
                orderLine(service, unit("CAI"), "1", "300000", null, vat)));

        long order = jsonId(send(buyer, post(ORDERS), body, newKey()).andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.totalAmount").value(12430000))
                .andExpect(jsonPath("$.data.invoiceSeries").value(series))
                .andExpect(jsonPath("$.data.supplierTaxCode").value("0300000001"))
                .andExpect(jsonPath("$.data.supplierAddress").isNotEmpty())
                .andExpect(jsonPath("$.data.dueDate").value("2026-11-04")).andReturn());
        // D-61: đơn nháp không cộng hàng đang về (ghi sổ là nhập kho ngay).
        assertThat(incoming(tube, warehouse)).isEqualByComparingTo(tubeIncoming);
        assertThat(incoming(machine, warehouse)).isEqualByComparingTo(machineIncoming);

        act(buyer, ORDERS, order, "post", null).andExpect(status().isForbidden());
        mockMvc.perform(as(buyer, get(ORDERS + "/" + order + "/stock-in-slip"))).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_DOCUMENT_STATE"));
        String key = newKey();
        MvcResult posted = postOrder(order, key).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("POSTED"))
                .andExpect(jsonPath("$.data.inventoryDocumentNo").isNotEmpty()).andReturn();
        postOrder(order, key).andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("POSTED"));
        act(keeper, ORDERS, order, "post", null).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_DOCUMENT_STATE"));
        long inventoryId = ((Number) json(posted, "$.data.inventoryDocumentId")).longValue();
        Map<String, Object> source = jdbc.queryForMap("SELECT document_type, status, source_type, source_id FROM "
                + "inventory_documents WHERE id = ?", inventoryId);
        assertThat(source).containsEntry("document_type", "PNK").containsEntry("status", "POSTED")
                .containsEntry("source_type", "DMH");
        assertThat(((Number) source.get("source_id")).longValue()).isEqualTo(order);
        assertThat(id("SELECT COUNT(*) FROM inventory_documents WHERE source_type = 'DMH' AND source_id = " + order))
                .isEqualTo(1);
        assertThat(incoming(tube, warehouse)).isEqualByComparingTo(tubeIncoming);
        assertThat(incoming(machine, warehouse)).isEqualByComparingTo(machineIncoming);
        assertThat(onHand(tube, warehouse)).isEqualByComparingTo(tubeOnHand.add(new BigDecimal("200")));
        assertThat(lotOnHand(lot)).isEqualByComparingTo("200");
        assertThat(id("SELECT COUNT(*) FROM inventory_serials WHERE serial_no = '" + serial + "'")).isEqualTo(1);
        assertThat(debtRemaining(order)).isEqualByComparingTo("12430000");
        assertThat(journal("DMH", order)).containsExactly("N:1561:1000000", "C:331:1000000", "N:1561:10000000",
                "C:331:10000000", "N:6422:300000", "C:331:300000", "N:1331:1130000", "C:331:1130000");
        assertLedgerConsistent(tube, warehouse);

        // Phiếu nhập kho in từ đơn: chỉ dòng hàng hóa, giá sau chiết khấu trước thuế, tài khoản phần hàng hóa
        mockMvc.perform(as(buyer, get(ORDERS + "/" + order + "/stock-in-slip"))).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.slipNo").value((String) json(posted, "$.data.inventoryDocumentNo")))
                .andExpect(jsonPath("$.data.companyName").isNotEmpty())
                .andExpect(jsonPath("$.data.supplierTaxCode").value("0300000001"))
                .andExpect(jsonPath("$.data.debitAccounts.length()").value(1))
                .andExpect(jsonPath("$.data.debitAccounts[0]").value("1561"))
                .andExpect(jsonPath("$.data.creditAccounts.length()").value(1))
                .andExpect(jsonPath("$.data.creditAccounts[0]").value("331"))
                .andExpect(jsonPath("$.data.description").value("Mua hàng của Nhà phân phối thiết bị mẫu theo hóa đơn 123"))
                .andExpect(jsonPath("$.data.lines.length()").value(2))
                .andExpect(jsonPath("$.data.lines[0].lotNo").value(lot))
                .andExpect(jsonPath("$.data.lines[0].unitPrice").value(500000))
                .andExpect(jsonPath("$.data.lines[1].lineNo").value(2))
                .andExpect(jsonPath("$.data.totalAmount").value(11000000))
                .andExpect(jsonPath("$.data.taxRates.length()").value(1))
                .andExpect(jsonPath("$.data.taxRates[0]").value(10))
                .andExpect(jsonPath("$.data.taxAmount").value(1100000))
                .andExpect(jsonPath("$.data.paymentAmount").value(12100000));

        // D-58: đầu phiếu theo công ty chọn khi in; ngoài phạm vi bị từ chối; in theo công ty khác ghi nhật ký
        String otherCode = unique("CT");
        jdbc.update("INSERT INTO companies (code, name, address, status, created_at, updated_at) "
                + "VALUES (?, 'Công ty in thử', '12 Phố In, Hà Nội', 'ACTIVE', UTC_TIMESTAMP(3), UTC_TIMESTAMP(3))",
                otherCode);
        Long other = jdbc.queryForObject("SELECT id FROM companies WHERE code = ?", Long.class, otherCode);
        mockMvc.perform(as(buyer, get(ORDERS + "/" + order + "/stock-in-slip")).param("companyId",
                        String.valueOf(other)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").value("companyId"));
        jdbc.update("INSERT INTO sys_user_data_scopes (user_id, scope_type, scope_id) "
                + "SELECT id, 'COMPANY', ? FROM sys_users WHERE username = 'muahang.hn'", other);
        try {
            Session scoped = login(mockMvc, "muahang.hn");
            mockMvc.perform(as(scoped, get(ORDERS + "/print-companies"))).andExpect(status().isOk())
                    .andExpect(jsonPath("$.data[?(@.id == 1)]").isNotEmpty())
                    .andExpect(jsonPath("$.data[?(@.id == " + other + ")].address").value("12 Phố In, Hà Nội"));
            mockMvc.perform(as(scoped, get(ORDERS + "/" + order + "/stock-in-slip")).param("companyId",
                            String.valueOf(other)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.companyName").value("Công ty in thử"))
                    .andExpect(jsonPath("$.data.companyAddress").value("12 Phố In, Hà Nội"));
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sys_audit_logs WHERE action = 'PRINT_AS_COMPANY' "
                    + "AND entity_type = 'PURCHASE_ORDER' AND entity_id = ?", Integer.class, order)).isEqualTo(1);
        } finally {
            jdbc.update("DELETE FROM sys_user_data_scopes WHERE scope_type = 'COMPANY' AND scope_id = ?", other);
        }

        send(buyer, post(ORDERS), orderWithInvoice(supplier, warehouse, series.toLowerCase(), " 123 ", List.of(
                orderLine(service, unit("CAI"), "1", "1000", null, null))), newKey())
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("DUPLICATE_DOCUMENT"));

        act(keeper, ORDERS, order, "reverse", "{\"reason\":\"Nhập nhầm\"}")
                .andExpect(jsonPath("$.data.status").value("REVERSED"));
        mockMvc.perform(as(buyer, get(ORDERS + "/" + order + "/stock-in-slip"))).andExpect(status().isConflict());
        assertThat(onHand(tube, warehouse)).isEqualByComparingTo(tubeOnHand);
        assertThat(lotOnHand(lot)).isEqualByComparingTo("0");
        assertThat(jdbc.queryForObject("SELECT status FROM debt_items WHERE source_type = 'DMH' AND source_id = ?",
                String.class, order)).isEqualTo("REVERSED");
        assertThat(id("SELECT COUNT(*) FROM journal_entries WHERE source_type = 'DMH' AND source_id = " + order
                + " AND reversal_of_id IS NOT NULL")).isEqualTo(1);
        assertThat(incoming(tube, warehouse)).isEqualByComparingTo(tubeIncoming);
        assertLedgerConsistent(tube, warehouse);
        send(buyer, post(ORDERS), orderWithInvoice(supplier, warehouse, series, "123", List.of(
                orderLine(service, unit("CAI"), "1", "1000", null, null))), newKey())
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("D-61, AT-10, AT-13: đơn mua từ ngưỡng chỉ người có quyền ghi sổ vượt ngưỡng ghi sổ được; không có bước duyệt; hủy đơn nháp")
    void thresholdAuthority() throws Exception {
        long machine = product("MAY_XN_01");
        long piece = unit("CAI");
        long warehouse = warehouse("KHO_HN");
        String body = order(partner("NCC_01"), warehouse, List.of(orderLine(machine, piece, "1", "60000000", null,
                null).replaceFirst("}$", ",\"serialNumbers\":[\"" + unique("SN") + "\"]}")));
        long order = jsonId(send(buyer, post(ORDERS), body, newKey()).andExpect(status().isCreated()).andReturn());
        BigDecimal incomingBefore = incoming(machine, warehouse);
        BigDecimal onHandBefore = onHand(machine, warehouse);

        mockMvc.perform(as(hcmKeeper, get(ORDERS + "/" + order))).andExpect(status().isNotFound());
        act(keeper, ORDERS, order, "post", null).andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("AUTHORITY_LIMIT_EXCEEDED"));
        assertThat(id("SELECT COUNT(*) FROM purchase_orders WHERE status = 'DRAFT' AND id = " + order)).isEqualTo(1);
        assertThat(onHand(machine, warehouse)).isEqualByComparingTo(onHandBefore);
        assertThat(id("SELECT COUNT(*) FROM approval_requests WHERE document_type = 'DMH' AND document_id = "
                + order)).isZero();

        String key = newKey();
        postOrder(order, key, director).andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("POSTED"));
        postOrder(order, key, director).andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("POSTED"));
        assertThat(onHand(machine, warehouse)).isEqualByComparingTo(onHandBefore.add(BigDecimal.ONE));
        assertThat(incoming(machine, warehouse)).isEqualByComparingTo(incomingBefore);
        assertThat(id("SELECT COUNT(*) FROM sys_audit_logs WHERE entity_type = 'PURCHASE_ORDER' AND entity_id = "
                + order + " AND action = 'POST' AND reason LIKE 'Ghi sổ đơn mua vượt ngưỡng%'")).isEqualTo(1);

        long draft = jsonId(send(buyer, post(ORDERS), body.replaceFirst("\"SN[^\"]*\"", "\"" + unique("SN") + "\""),
                newKey()).andExpect(status().isCreated()).andReturn());
        act(buyer, ORDERS, draft, "cancel", "{\"reason\":\"Đổi nhà cung cấp\"}")
                .andExpect(jsonPath("$.data.status").value("CANCELLED"));
        assertThat(incoming(machine, warehouse)).isEqualByComparingTo(incomingBefore);
    }

    @Test
    @DisplayName("D-61: đơn chờ duyệt lập trước D-61 không ghi sổ được, chỉ hủy; hủy đóng yêu cầu duyệt còn treo; lịch sử duyệt chỉ đọc")
    void legacyPendingApproval() throws Exception {
        String body = order(partner("NCC_01"), warehouse("KHO_HN"), List.of(orderLine(product("DV_VC"), unit("CAI"),
                "1", "100000", null, null)));
        long order = jsonId(send(buyer, post(ORDERS), body, newKey()).andExpect(status().isCreated()).andReturn());
        long request = legacyPendingRequest(order);

        act(keeper, ORDERS, order, "post", null).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_DOCUMENT_STATE"));
        mockMvc.perform(as(buyer, get(ORDERS + "/" + order))).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.approvals.length()").value(1))
                .andExpect(jsonPath("$.data.approvals[0].status").value("PENDING"));
        act(buyer, ORDERS, order, "cancel", "{\"reason\":\"Bỏ đơn cũ\"}")
                .andExpect(jsonPath("$.data.status").value("CANCELLED"));
        assertThat(jdbc.queryForObject("SELECT status FROM approval_requests WHERE id = ?", String.class, request))
                .isEqualTo("CANCELLED");
        assertThat(id("SELECT COUNT(*) FROM approval_actions WHERE request_id = " + request
                + " AND action = 'CANCEL'")).isEqualTo(1);
    }

    /** Giả lập đơn đã gửi duyệt trước D-61: yêu cầu duyệt đang chờ theo luồng cũ (đã ngừng hoạt động). */
    private long legacyPendingRequest(long order) {
        long requester = id("SELECT id FROM sys_users WHERE username = 'muahang.hn'");
        jdbc.update("INSERT INTO approval_workflows (company_id, document_type, name, branch_id, priority, status, "
                + "created_at, updated_at) VALUES (1, 'DMH', ?, NULL, 0, 'INACTIVE', UTC_TIMESTAMP(3), "
                + "UTC_TIMESTAMP(3))", unique("Cũ - DMH "));
        long workflow = id("SELECT MAX(id) FROM approval_workflows");
        jdbc.update("UPDATE purchase_orders SET status = 'SUBMITTED', submitted_by = ?, submitted_at = UTC_TIMESTAMP(3) "
                + "WHERE id = ?", requester, order);
        jdbc.update("INSERT INTO approval_requests (company_id, branch_id, document_type, document_id, document_no, "
                + "amount, workflow_id, current_step, total_steps, status, requested_by, requested_at) SELECT "
                + "company_id, branch_id, 'DMH', id, document_no, total_amount, ?, 1, 1, 'PENDING', ?, UTC_TIMESTAMP(3) "
                + "FROM purchase_orders WHERE id = ?", workflow, requester, order);
        long request = id("SELECT id FROM approval_requests WHERE document_type = 'DMH' AND document_id = " + order);
        jdbc.update("INSERT INTO approval_request_steps (request_id, step_no, approver_type, role_id, user_id) "
                + "SELECT ?, 1, 'ROLE', id, NULL FROM sys_roles WHERE code = 'DIRECTOR'", request);
        jdbc.update("INSERT INTO approval_actions (request_id, step_no, action, actor_user_id, acted_at) "
                + "VALUES (?, 1, 'SUBMIT', ?, UTC_TIMESTAMP(3))", request, requester);
        return request;
    }

    @Test
    @DisplayName("BR-PUR-04, 08, 09: nhà cung cấp, kho cùng chi nhánh, VND, phạm vi chi nhánh, lô / hạn dùng, serial, hóa đơn đủ thông tin")
    void validation() throws Exception {
        long tube = product("VT_ONG_01");
        long piece = unit("CAI");
        long supplier = partner("NCC_01");
        long warehouse = warehouse("KHO_HN");
        String line = lotLine(tube, piece, "1", "1000", null, unique("L"));
        send(buyer, post(ORDERS), order(partner("KH_BV01"), warehouse, List.of(line)), newKey())
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").value("supplierId"));
        send(buyer, post(ORDERS), order(supplier, warehouse("KHO_HCM"), List.of(line)), newKey())
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").value("warehouseId"));
        send(buyer, post(ORDERS), order(supplier, warehouse, List.of(line))
                .replace("\"lines\"", "\"currencyCode\":\"USD\",\"lines\""), newKey())
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").value("currencyCode"));
        send(buyer, post(ORDERS), order(supplier, warehouse, List.of(line)).replace("\"branchId\":1", "\"branchId\":2"),
                newKey()).andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").value("branchId"));
        send(buyer, post(ORDERS), order(supplier, warehouse, List.of(orderLine(tube, piece, "1", "1000", null, null))),
                newKey()).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("lines[0].lotNo"));
        send(buyer, post(ORDERS), order(supplier, warehouse, List.of(orderLine(product("MAY_XN_01"), piece, "2",
                "1000", null, null).replaceFirst("}$", ",\"serialNumbers\":[\"" + unique("SN") + "\"]}"))), newKey())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("lines[0].serialNumbers"));
        // Hàng không quản lý lô: chặn số lô ngay khi lưu đơn, không để đến lúc ghi sổ mới lỗi
        send(buyer, post(ORDERS), order(supplier, warehouse, List.of(lotLine(product("MAY_XN_01"), piece, "1",
                "1000", null, unique("L")).replaceFirst("}$", ",\"serialNumbers\":[\"" + unique("SN") + "\"]}"))),
                newKey()).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("lines[0].lotNo"));
        send(buyer, post(ORDERS), order(supplier, warehouse, List.of(line.replaceFirst("}$",
                ",\"serialNumbers\":[\"" + unique("SN") + "\"]}"))), newKey())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("lines[0].serialNumbers"));
        send(buyer, post(ORDERS), order(supplier, warehouse, List.of(line))
                .replace("\"lines\"", "\"invoiceSeries\":\"C26TAA\",\"lines\""), newKey())
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").value("invoiceNo"));
    }

    @Test
    @DisplayName("BR-PUR-04, BR-UX-05: đọc dòng hàng đơn mua từ Excel để điền form; lỗi theo dòng; không lưu đơn")
    void importLines() throws Exception {
        mockMvc.perform(as(buyer, get(ORDERS + "/line-import-template"))).andExpect(status().isOk());
        long tube = product("VT_ONG_01");
        long vat = taxRate("VAT10");
        long ordersBefore = jdbc.queryForObject("SELECT COUNT(*) FROM purchase_orders", Long.class);

        importLines(workbookOf(List.of(
                new String[] {"VT_ONG_01", "HOP", "2", "500000"},
                new String[] {"KHONG_CO_MA", "", "1", "1000"})))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[*].field").value(
                        hasItems("rows[2].lotNo", "rows[3].productId")));

        importLines(workbookOf(List.of(
                new String[] {"vt_ong_01", "HOP", "2", "500000", "5", "VAT10", "LO-X", "", "31/12/2027"},
                new String[] {"DV_VC", "", "1", "200000", "", "", "LO-BO-QUA"})))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.created").value(2))
                .andExpect(jsonPath("$.data.lines[0].productId").value(tube))
                .andExpect(jsonPath("$.data.lines[0].unitId").value(unit("HOP")))
                .andExpect(jsonPath("$.data.lines[0].taxRateId").value(vat))
                .andExpect(jsonPath("$.data.lines[0].discountPercent").value(5))
                .andExpect(jsonPath("$.data.lines[0].expiryDate").value("2027-12-31"))
                .andExpect(jsonPath("$.data.lines[1].serviceLine").value(true))
                .andExpect(jsonPath("$.data.lines[1].lotNo").doesNotExist());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM purchase_orders", Long.class)).isEqualTo(ordersBefore);
    }

    private ResultActions importLines(byte[] content) throws Exception {
        return mockMvc.perform(as(buyer, multipart(ORDERS + "/line-import")
                        .file(new MockMultipartFile("file", "dong-don-mua.xlsx", "application/octet-stream", content)))
                .param("companyId", "1").param("documentDate", "2026-10-10"));
    }

    private static byte[] workbookOf(List<String[]> rows) throws Exception {
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("Dong don mua");
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

    private ResultActions postOrder(long order, String key) throws Exception {
        return postOrder(order, key, keeper);
    }

    private ResultActions postOrder(long order, String key, Session user) throws Exception {
        return mockMvc.perform(as(user, post(ORDERS + "/" + order + "/post")).header(IdempotentCommand.HEADER, key));
    }
}
