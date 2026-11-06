package com.company.erp.inventory;

import static com.company.erp.support.ApiTestSupport.json;
import static com.company.erp.support.ApiTestSupport.newKey;
import static com.company.erp.support.ApiTestSupport.unique;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.company.erp.support.MySqlIntegrationTest;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Máy mới / máy cũ (D-70, BR-INV-43) trên MySQL thật: khai báo khi nhập hàng nhóm máy (nhóm MAY và nhóm con), tồn
 * tách theo serial / lô, đảo chứng từ khôi phục tình trạng serial, chặn khai báo sai.
 */
@MySqlIntegrationTest
@AutoConfigureMockMvc
class MachineConditionIntegrationTest extends InventoryIntegrationSupport {

    @Autowired
    MachineConditionIntegrationTest(MockMvc mockMvc, JdbcTemplate jdbc) {
        super(mockMvc, jdbc);
    }

    @Test
    @DisplayName("BR-INV-43, AT-09: nhập máy cũ theo serial, lọc tồn máy cũ, đảo phiếu xuất khôi phục tình trạng")
    void serialConditionFollowsReceiptsAndReversals() throws Exception {
        long warehouse = warehouse(1);
        long machine = machine(false, true);
        String fresh = unique("SN");
        String used = unique("SU");
        createAndPost(manager, "PNK", warehouse, List.of(
                condition(receiptLine(machine, 1, null, null, List.of(fresh)), "NEW"),
                condition(receiptLine(machine, 1, null, null, List.of(used)), "USED")));
        assertThat(serialCondition(machine, fresh)).isEqualTo("NEW");
        assertThat(serialCondition(machine, used)).isEqualTo("USED");

        MvcResult usedStock = mockMvc.perform(as(manager, get("/api/v1/inventory/stock-balances"))
                        .param("companyId", "1").param("productId", String.valueOf(machine))
                        .param("itemCondition", "USED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].itemCondition").value("USED"))
                .andReturn();
        List<String> usedSerials = json(usedStock, "$.data.items[*].serialNo");
        assertThat(usedSerials).containsExactly(used);

        long issue = createAndPost(manager, "PXK", warehouse, List.of(serialLine(machine, used, null)));
        long adjustIn = createAndPost(manager, "PDC", warehouse, List.of(
                condition(serialLine(machine, used, "\"direction\":\"IN\",\"unitCost\":1000"), "NEW")));
        assertThat(serialCondition(machine, used)).isEqualTo("NEW");

        postDocument(manager, reverse(adjustIn));
        postDocument(manager, reverse(issue));
        assertThat(serialCondition(machine, used)).isEqualTo("USED");
        assertThat(count("SELECT COUNT(*) FROM inventory_transactions t JOIN inventory_serials s ON s.id = t.serial_id"
                + " WHERE s.product_id = " + machine + " AND s.serial_no = '" + used + "' AND t.item_condition IS NULL"))
                .isZero();
        assertLedgerConsistent(machine);
    }

    @Test
    @DisplayName("BR-INV-43: lô máy cố định một tình trạng; chặn khai báo cho hàng ngoài nhóm máy, dòng xuất, hàng không"
            + " theo serial / lô")
    void rejectsInvalidConditions() throws Exception {
        long warehouse = warehouse(1);
        long lotMachine = machine(true, false);
        String lotNo = unique("LM");
        createAndPost(manager, "PNK", warehouse, List.of(
                condition(receiptLine(lotMachine, 2, lotNo, "2030-12-31", null), "USED")));
        assertThat(jdbc.queryForObject("SELECT item_condition FROM inventory_lots WHERE product_id = ? AND lot_no = ?",
                String.class, lotMachine, lotNo)).isEqualTo("USED");
        long sameLotNew = create(manager, "PNK", warehouse, List.of(
                condition(receiptLine(lotMachine, 1, lotNo, "2030-12-31", null), "NEW")));
        action(manager, sameLotNew, "post", null).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("lines[0].lotNo"));

        long consumable = product(true, true, false);
        rejected(document("PNK", warehouse,
                List.of(condition(receiptLine(consumable, 1, unique("L"), "2030-12-31", null), "NEW")), "2026-10-05"));
        rejected(document("PXK", warehouse, List.of(condition(issueLine(lotMachine, 1, null, null), "NEW")),
                "2026-10-05"));
        long untracked = machine(false, false);
        rejected(document("PNK", warehouse, List.of(condition(receiptLine(untracked, 1, null, null, null), "USED")),
                "2026-10-05"));
        createAndPost(manager, "PNK", warehouse, List.of(
                condition(receiptLine(untracked, 1, null, null, null), "NEW")));
        assertLedgerConsistent(lotMachine);
    }

    @Test
    @DisplayName("BR-INV-43: danh sách nhóm hàng máy gồm nhóm MAY và nhóm con, không gồm nhóm khác")
    void machineCategories() throws Exception {
        MvcResult result = mockMvc.perform(as(manager, get("/api/v1/inventory/machine-categories"))
                        .param("companyId", "1"))
                .andExpect(status().isOk()).andReturn();
        List<Integer> ids = json(result, "$.data[*]");
        assertThat(ids).contains((int) id("SELECT id FROM product_categories WHERE company_id = 1 AND code = 'MAY'"),
                (int) id("SELECT id FROM product_categories WHERE company_id = 1 AND code = 'MAY_XN'"))
                .doesNotContain((int) categoryId);
    }

    /** Hàng thuộc nhóm con MAY_XN của nhóm máy MAY (tham số inventory.machine-category-code mặc định). */
    private long machine(boolean lot, boolean serial) throws Exception {
        long machineCategory = id("SELECT id FROM product_categories WHERE company_id = 1 AND code = 'MAY_XN'");
        String code = unique("M");
        String body = "{\"companyId\":1,\"code\":\"" + code + "\",\"name\":\"Máy " + code + "\",\"categoryId\":"
                + machineCategory + ",\"productType\":\"MACHINE\",\"baseUnitId\":" + unitId + ",\"lotTracked\":" + lot
                + ",\"expiryTracked\":false,\"serialTracked\":" + serial + "}";
        return jsonValue(send(admin, post("/api/v1/products"), body, newKey()).andExpect(status().isCreated())
                .andReturn(), "$.data.id", Integer.class);
    }

    private String serialLine(long product, String serial, String extra) {
        return "{\"productId\":" + product + ",\"unitId\":" + unitId + ",\"quantity\":1,\"serialNumbers\":[\""
                + serial + "\"]" + (extra == null ? "" : "," + extra) + "}";
    }

    private static String condition(String line, String condition) {
        return line.substring(0, line.length() - 1) + ",\"itemCondition\":\"" + condition + "\"}";
    }

    private void rejected(String body) throws Exception {
        send(manager, post(DOCS), body, newKey()).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("lines[0].itemCondition"));
    }

    private String serialCondition(long product, String serial) {
        return jdbc.queryForObject("SELECT item_condition FROM inventory_serials WHERE product_id = ? AND serial_no = ?",
                String.class, product, serial);
    }
}
