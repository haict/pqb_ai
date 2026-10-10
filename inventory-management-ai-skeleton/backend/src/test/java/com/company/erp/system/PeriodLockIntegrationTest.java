package com.company.erp.system;

import static com.company.erp.support.ApiTestSupport.createUser;
import static com.company.erp.support.ApiTestSupport.json;
import static com.company.erp.support.ApiTestSupport.login;
import static com.company.erp.support.ApiTestSupport.newKey;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.company.erp.common.application.IdempotentCommand;
import com.company.erp.common.domain.BusinessException;
import com.company.erp.common.domain.ErrorCode;
import com.company.erp.common.domain.PeriodModule;
import com.company.erp.support.ApiTestSupport.Session;
import com.company.erp.support.MySqlIntegrationTest;
import com.company.erp.system.application.PeriodGuard;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * AT-11 (phần khung): khóa/mở kỳ theo chi nhánh × phân hệ, thứ tự kỳ, quyền mở lại; chặn ghi sổ vào kỳ khóa.
 * Mỗi test dùng một năm riêng, cách nhau ít nhất một năm, để tháng 1 không phụ thuộc tháng 12 của test khác.
 */
@MySqlIntegrationTest
@AutoConfigureMockMvc
class PeriodLockIntegrationTest {

    private static final long HN = 1L;
    private static final long HCM = 2L;

    private final MockMvc mockMvc;
    private final JdbcTemplate jdbc;
    private final PeriodGuard periodGuard;
    private final TransactionTemplate transactionTemplate;

    @Autowired
    PeriodLockIntegrationTest(MockMvc mockMvc, JdbcTemplate jdbc, PeriodGuard periodGuard,
                              TransactionTemplate transactionTemplate) {
        this.mockMvc = mockMvc;
        this.jdbc = jdbc;
        this.periodGuard = periodGuard;
        this.transactionTemplate = transactionTemplate;
    }

    @Test
    @DisplayName("AT-11, BR-GEN-10: kỳ kho đã khóa chặn ghi sổ đúng chi nhánh, đúng phân hệ; ngày chưa có kỳ bị chặn")
    void lockedPeriodBlocksPosting() throws Exception {
        Session accountant = login(mockMvc, "ketoantruong");
        generate(accountant, 2050);
        long january = periodId(2050, 1);

        lock(accountant, january, PeriodModule.INVENTORY, HN).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.locked").value(true));

        LocalDate postingDate = LocalDate.of(2050, 1, 15);
        assertThatThrownBy(() -> assertOpen(HN, PeriodModule.INVENTORY, postingDate))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).errorCode()).isEqualTo(ErrorCode.PERIOD_LOCKED));
        assertOpen(HCM, PeriodModule.INVENTORY, postingDate);
        assertOpen(HN, PeriodModule.DEBT, postingDate);
        assertOpen(HN, PeriodModule.INVENTORY, LocalDate.of(2050, 2, 1));
        assertThatThrownBy(() -> assertOpen(HN, PeriodModule.INVENTORY, LocalDate.of(2099, 1, 1)))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).errorCode())
                        .isEqualTo(ErrorCode.PERIOD_NOT_DEFINED));

        MvcResult list = mockMvc.perform(as(accountant, get("/api/v1/fiscal-periods"))
                        .param("companyId", "1").param("year", "2050").param("branchId", "1"))
                .andExpect(status().isOk())
                .andReturn();
        List<Boolean> inventoryLocks = json(list, "$.data[*].locks[?(@.module == 'INVENTORY')].locked");
        assertThat(inventoryLocks).hasSize(12).first().isEqualTo(true);
        assertThat(inventoryLocks.subList(1, 12)).containsOnly(false);
        List<String> changedBy = json(list, "$.data[0].locks[?(@.module == 'INVENTORY')].changedBy");
        assertThat(changedBy).containsExactly("ketoantruong");
    }

    @Test
    @DisplayName("BR-SYS-31: khóa theo thứ tự tháng; kế toán sau kho; mở lại theo thứ tự ngược")
    void lockOrderIsEnforced() throws Exception {
        Session accountant = login(mockMvc, "ketoantruong");
        generate(accountant, 2052);
        long january = periodId(2052, 1);
        long february = periodId(2052, 2);

        lock(accountant, february, PeriodModule.INVENTORY, HN).andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("PERIOD_ORDER_VIOLATION"));
        lock(accountant, january, PeriodModule.ACCOUNTING, HN).andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("PERIOD_CLOSE_PRECONDITION"));

        lock(accountant, january, PeriodModule.INVENTORY, HN).andExpect(status().isOk());
        lock(accountant, january, PeriodModule.INVENTORY, HN).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_PERIOD_STATE"));
        lock(accountant, january, PeriodModule.ACCOUNTING, HN).andExpect(status().isOk());
        lock(accountant, february, PeriodModule.INVENTORY, HN).andExpect(status().isOk());

        unlock(accountant, january, PeriodModule.INVENTORY, "Điều chỉnh", HN)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("PERIOD_ORDER_VIOLATION"));
        unlock(accountant, february, PeriodModule.INVENTORY, "Điều chỉnh tháng 2", HN).andExpect(status().isOk());
        unlock(accountant, january, PeriodModule.INVENTORY, "Điều chỉnh", HN)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("PERIOD_ORDER_VIOLATION"));
        unlock(accountant, january, PeriodModule.ACCOUNTING, "Điều chỉnh", HN).andExpect(status().isOk());
        unlock(accountant, january, PeriodModule.INVENTORY, "Điều chỉnh", HN).andExpect(status().isOk());
    }

    @Test
    @DisplayName("AT-10, BR-SYS-32: mở lại kỳ chỉ Kế toán trưởng/Giám đốc, bắt buộc lý do, ghi audit")
    void unlockRequiresPermissionAndReason() throws Exception {
        Session accountant = login(mockMvc, "ketoantruong");
        generate(accountant, 2054);
        long january = periodId(2054, 1);
        lock(accountant, january, PeriodModule.CASH, HN, HCM).andExpect(status().isOk());

        createUser(jdbc, "ketoanthue.1c", "TAX_ACCOUNTANT", "COMPANY", 1, HN, false);
        Session taxAccountant = login(mockMvc, "ketoanthue.1c");
        unlock(taxAccountant, january, PeriodModule.CASH, "Không có quyền", HN).andExpect(status().isForbidden());

        Session admin = login(mockMvc, "admin");
        lock(admin, january, PeriodModule.DEBT, HN).andExpect(status().isForbidden());
        Session warehouse = login(mockMvc, "kho.hcm");
        mockMvc.perform(as(warehouse, get("/api/v1/fiscal-periods"))
                        .param("companyId", "1").param("year", "2054").param("branchId", "2"))
                .andExpect(status().isForbidden());

        Session director = login(mockMvc, "giamdoc");
        unlock(director, january, PeriodModule.CASH, " ", HN).andExpect(status().isBadRequest());
        unlock(director, january, PeriodModule.CASH, "Bổ sung phiếu chi", HN).andExpect(status().isOk());

        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM sys_audit_logs
                WHERE entity_type = 'FISCAL_PERIOD' AND entity_id = ? AND action = 'UNLOCK_PERIOD'
                  AND reason = 'Bổ sung phiếu chi' AND actor_user_id = 2 AND branch_id = 1
                """, Integer.class, january)).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "SELECT locked FROM period_locks WHERE fiscal_period_id = ? AND branch_id = 2 AND module = 'CASH'",
                Boolean.class, january)).isTrue();
    }

    @Test
    @DisplayName("BR-SYS-06: chi nhánh ngoài phạm vi trả 404 khi xem và khi khóa kỳ")
    void outOfScopeBranchIsNotFound() throws Exception {
        Session accountant = login(mockMvc, "ketoantruong");
        generate(accountant, 2056);
        long january = periodId(2056, 1);

        Session branchDirector = login(mockMvc, "giamdoc.hn");
        mockMvc.perform(as(branchDirector, get("/api/v1/fiscal-periods"))
                        .param("companyId", "1").param("year", "2056").param("branchId", "2"))
                .andExpect(status().isNotFound());

        createUser(jdbc, "ketoanthue.hn", "TAX_ACCOUNTANT", "BRANCH", HN, HN, false);
        Session branchAccountant = login(mockMvc, "ketoanthue.hn");
        lock(branchAccountant, january, PeriodModule.INVENTORY, HN, HCM).andExpect(status().isNotFound());
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM period_locks WHERE fiscal_period_id = ? AND locked = TRUE",
                Integer.class, january)).isZero();
        lock(branchAccountant, january, PeriodModule.INVENTORY, HN).andExpect(status().isOk());
    }

    @Test
    @DisplayName("AT-13: gửi lại cùng Idempotency-Key không khóa lại, không ghi audit trùng")
    void lockIsIdempotent() throws Exception {
        Session accountant = login(mockMvc, "ketoantruong");
        generate(accountant, 2058);
        long january = periodId(2058, 1);
        String key = newKey();

        lockWithKey(accountant, january, key, PeriodModule.DEBT, HN).andExpect(status().isOk());
        lockWithKey(accountant, january, key, PeriodModule.DEBT, HN).andExpect(status().isOk());

        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM sys_audit_logs WHERE entity_type = 'FISCAL_PERIOD' AND entity_id = ? "
                        + "AND action = 'LOCK_PERIOD'", Integer.class, january)).isEqualTo(1);
    }

    @Test
    @DisplayName("Hai người khóa cùng kỳ đồng thời: đúng một người thành công, không lỗi hệ thống")
    void concurrentLockSucceedsOnce() throws Exception {
        Session accountant = login(mockMvc, "ketoantruong");
        generate(accountant, 2060);
        long january = periodId(2060, 1);
        int threads = 6;
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        List<Future<Integer>> results = new ArrayList<>();
        try {
            for (int i = 0; i < threads; i++) {
                results.add(executor.submit(() -> {
                    start.await();
                    return lock(accountant, january, PeriodModule.INVENTORY, HN, HCM)
                            .andReturn().getResponse().getStatus();
                }));
            }
            start.countDown();
            List<Integer> statuses = new ArrayList<>();
            for (Future<Integer> result : results) {
                statuses.add(result.get(60, TimeUnit.SECONDS));
            }
            assertThat(statuses).containsOnlyOnce(200).containsOnly(200, 409);
        } finally {
            executor.shutdownNow();
        }
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM sys_audit_logs WHERE entity_type = 'FISCAL_PERIOD' AND entity_id = ? "
                        + "AND action = 'LOCK_PERIOD'", Integer.class, january)).isEqualTo(2);
    }

    private void assertOpen(long branchId, PeriodModule module, LocalDate postingDate) {
        transactionTemplate.executeWithoutResult(status -> periodGuard.assertOpen(1L, branchId, module, postingDate));
    }

    private void generate(Session session, int year) throws Exception {
        mockMvc.perform(as(session, post("/api/v1/fiscal-periods/generate"))
                        .header(IdempotentCommand.HEADER, newKey()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"companyId\":1,\"year\":" + year + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.created").value(12));
    }

    private long periodId(int year, int month) {
        return jdbc.queryForObject("SELECT id FROM fiscal_periods WHERE company_id = 1 AND period_year = ? "
                + "AND period_month = ?", Long.class, year, month);
    }

    private ResultActions lock(Session session, long periodId, PeriodModule module, Long... branchIds)
            throws Exception {
        return lockWithKey(session, periodId, newKey(), module, branchIds);
    }

    private ResultActions lockWithKey(Session session, long periodId, String key, PeriodModule module,
                                      Long... branchIds) throws Exception {
        return mockMvc.perform(as(session, post("/api/v1/fiscal-periods/" + periodId + "/lock"))
                .header(IdempotentCommand.HEADER, key).contentType(MediaType.APPLICATION_JSON)
                .content("{\"module\":\"" + module + "\",\"branchIds\":" + List.of(branchIds) + "}"));
    }

    private ResultActions unlock(Session session, long periodId, PeriodModule module, String reason,
                                 Long... branchIds) throws Exception {
        return mockMvc.perform(as(session, post("/api/v1/fiscal-periods/" + periodId + "/unlock"))
                .header(IdempotentCommand.HEADER, newKey()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"module\":\"" + module + "\",\"branchIds\":" + List.of(branchIds)
                        + ",\"reason\":\"" + reason + "\"}"));
    }

    private static MockHttpServletRequestBuilder as(Session session, MockHttpServletRequestBuilder request) {
        return request.header(HttpHeaders.AUTHORIZATION, session.bearer());
    }
}
