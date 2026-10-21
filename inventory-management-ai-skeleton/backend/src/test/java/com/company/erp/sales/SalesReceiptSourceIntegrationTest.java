package com.company.erp.sales;

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
import org.springframework.test.web.servlet.ResultActions;

/**
 * Chọn chứng từ nhập cho dòng đơn bán (D-63, F8 xuất đích danh) trên MySQL thật: danh sách dòng nhập còn tồn theo FEFO,
 * lưu liên kết, chặn lô / hàng không khớp, phân quyền (AT-10); ghi sổ xuất đúng lô đã chọn.
 */
@MySqlIntegrationTest
@AutoConfigureMockMvc
class SalesReceiptSourceIntegrationTest extends SalesIntegrationSupport {

    @Autowired
    SalesReceiptSourceIntegrationTest(MockMvc mockMvc, JdbcTemplate jdbc) {
        super(mockMvc, jdbc);
    }

    @Test
    @DisplayName("D-63, AT-10: F8 liệt kê chứng từ nhập còn tồn (FEFO), lưu liên kết, chặn lô không khớp, xuất đúng lô")
    void pickReceiptSource() throws Exception {
        long item = newProduct(true, false);
        long piece = unit("CAI");
        long store = warehouse("KHO_HN");
        String later = unique("L");
        String earlier = unique("L");
        stockIn(item, "5", later, "2028-06-30", null);
        stockIn(item, "3", earlier, "2028-01-31", null);

        sources(courier, store, item).andExpect(status().isForbidden());
        MvcResult listed = sources(manager, store, item).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].lotNo").value(earlier))
                .andExpect(jsonPath("$.data[0].availableQuantity").value(3))
                .andExpect(jsonPath("$.data[1].lotNo").value(later))
                .andExpect(jsonPath("$.data[1].documentNo").isNotEmpty())
                .andExpect(jsonPath("$.data[1].unitCost").value(50000)).andReturn();
        long laterReceipt = ((Number) json(listed, "$.data[1].transactionId")).longValue();
        String laterNo = json(listed, "$.data[1].documentNo");
        sources(manager, store, newProduct(false, false)).andExpect(jsonPath("$.data.length()").value(0));

        String wrongLot = pickedLine(item, piece, "2", earlier, laterReceipt);
        send(manager, post(ORDERS), order(partner("KH_BV01"), store, false, List.of(wrongLot)), newKey())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("lines[0].lotNo"));
        String otherProduct = pickedLine(newProduct(true, false), piece, "1", later, laterReceipt);
        send(manager, post(ORDERS), order(partner("KH_BV01"), store, false, List.of(otherProduct)), newKey())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("lines[0].receiptTransactionId"));

        long order = jsonId(send(manager, post(ORDERS), order(partner("KH_BV01"), store, false, List.of(
                pickedLine(item, piece, "2", later, laterReceipt))), newKey())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.lines[0].receiptTransactionId").value(laterReceipt))
                .andExpect(jsonPath("$.data.lines[0].receiptNo").value(laterNo)).andReturn());
        postWithAuthority(keeper, order).andExpect(jsonPath("$.data.status").value("POSTED"));
        assertThat(lotOnHand(later)).isEqualByComparingTo("3");
        assertThat(lotOnHand(earlier)).isEqualByComparingTo("3");
    }

    private ResultActions sources(com.company.erp.support.ApiTestSupport.Session session, long store, long product)
            throws Exception {
        return mockMvc.perform(as(session, get(ORDERS + "/receipt-sources")).param("branchId", "1")
                .param("warehouseId", String.valueOf(store)).param("productId", String.valueOf(product)));
    }

    /** Dòng chọn chứng từ nhập: lô chỉ định (khác FEFO thì kèm lý do) và id dòng nhập. */
    private static String pickedLine(long product, long unit, String quantity, String lot, long receipt) {
        return line(product, unit, quantity, "100000", null).replaceFirst("}$", ",\"lotNo\":\"" + lot
                + "\",\"lotOverrideReason\":\"Chọn theo chứng từ nhập\",\"receiptTransactionId\":" + receipt + "}");
    }
}
