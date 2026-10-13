package com.company.erp.masterdata;

import static com.company.erp.support.ApiTestSupport.createUser;
import static com.company.erp.support.ApiTestSupport.json;
import static com.company.erp.support.ApiTestSupport.login;
import static com.company.erp.support.ApiTestSupport.newKey;
import static com.company.erp.support.ApiTestSupport.unique;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.company.erp.common.application.IdempotentCommand;
import com.company.erp.masterdata.application.MasterDataQueryService;
import com.company.erp.support.ApiTestSupport.Session;
import com.company.erp.support.MySqlIntegrationTest;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** Giai đoạn 2 - danh mục (BR-MD-01..46): CRUD, ràng buộc, phạm vi dữ liệu, ẩn giá mua, nhập Excel. */
@MySqlIntegrationTest
@AutoConfigureMockMvc
class MasterDataIntegrationTest {

    private final MockMvc mockMvc;
    private final JdbcTemplate jdbc;
    private final MasterDataQueryService queryService;

    @Autowired
    MasterDataIntegrationTest(MockMvc mockMvc, JdbcTemplate jdbc, MasterDataQueryService queryService) {
        this.mockMvc = mockMvc;
        this.jdbc = jdbc;
        this.queryService = queryService;
    }

    @Test
    @DisplayName("Danh mục mã - tên: tạo chống trùng, mã trùng 409, quốc gia sai 400, không có quyền 403")
    void simpleCatalogs() throws Exception {
        Session admin = login(mockMvc, "admin");
        String code = unique("U");
        String key = newKey();
        String body = "{\"companyId\":1,\"code\":\"" + code + "\",\"name\":\"Đơn vị test\"}";
        Integer first = json(send(admin, post("/api/v1/units"), body, key).andExpect(status().isCreated()).andReturn(),
                "$.data.id");
        Integer replay = json(send(admin, post("/api/v1/units"), body, key).andExpect(status().isCreated()).andReturn(),
                "$.data.id");
        assertThat(replay).isEqualTo(first);
        send(admin, post("/api/v1/units"), body, newKey()).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE_CODE"));
        send(admin, post("/api/v1/units"), "{\"companyId\":1,\"code\":\"" + unique("U")
                + "\",\"name\":\"Có quốc gia\",\"countryCode\":\"VN\"}", newKey())
                .andExpect(status().isBadRequest());
        send(admin, post("/api/v1/manufacturers"), "{\"companyId\":1,\"code\":\"" + unique("M")
                + "\",\"name\":\"NSX\",\"countryCode\":\"ZZ\"}", newKey())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("countryCode"));

        Session sales = login(mockMvc, "kinhdoanh.hn");
        mockMvc.perform(as(sales, get("/api/v1/units")).param("companyId", "1")).andExpect(status().isOk());
        send(sales, post("/api/v1/brands"), "{\"companyId\":1,\"code\":\"" + unique("B") + "\",\"name\":\"X\"}",
                newKey()).andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("BR-MD-04, D-09: cờ lô/hạn/serial hợp lệ; thuốc thử nhập ở trạng thái chờ kiểm tra; quy đổi ĐVT theo hiệu lực")
    void productRules() throws Exception {
        Session admin = login(mockMvc, "admin");
        long category = id("SELECT id FROM product_categories WHERE company_id = 1 AND code = 'TT'");
        long box = id("SELECT id FROM units WHERE company_id = 1 AND code = 'HOP'");
        long ml = id("SELECT id FROM units WHERE company_id = 1 AND code = 'ML'");

        send(admin, post("/api/v1/products"), product(unique("P"), "REAGENT", category, box, false, true, false),
                newKey()).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("expiryTracked"));
        send(admin, post("/api/v1/products"), product(unique("P"), "SERVICE", category, box, false, false, true),
                newKey()).andExpect(status().isBadRequest());

        MvcResult created = send(admin, post("/api/v1/products"),
                product(unique("P"), "REAGENT", category, box, true, true, false), newKey())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.initialQualityStatus").value("PENDING_INSPECTION"))
                .andReturn();
        Integer productId = json(created, "$.data.id");

        String unit = "{\"unitId\":" + ml + ",\"conversionFactor\":780,\"effectiveFrom\":\"2026-01-01\"}";
        mockMvc.perform(as(admin, post("/api/v1/products/" + productId + "/units"))
                        .contentType(MediaType.APPLICATION_JSON).content(unit))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.units.length()").value(1));
        mockMvc.perform(as(admin, post("/api/v1/products/" + productId + "/units"))
                        .contentType(MediaType.APPLICATION_JSON).content(unit))
                .andExpect(status().isConflict());
        mockMvc.perform(as(admin, post("/api/v1/products/" + productId + "/units"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"unitId\":" + ml + ",\"conversionFactor\":800,\"effectiveFrom\":\"2026-07-01\"}"))
                .andExpect(status().isOk());

        assertThat(queryService.conversionFactor(productId.longValue(), ml, LocalDate.of(2026, 3, 1)))
                .isEqualByComparingTo(new BigDecimal("780"));
        assertThat(queryService.conversionFactor(productId.longValue(), ml, LocalDate.of(2026, 8, 1)))
                .isEqualByComparingTo(new BigDecimal("800"));
        assertThat(queryService.conversionFactor(productId.longValue(), box, LocalDate.of(2026, 8, 1)))
                .isEqualByComparingTo(BigDecimal.ONE);
    }

    @Test
    @DisplayName("BR-MD-03: nhóm hàng không tạo vòng lặp cha - con")
    void categoryCycle() throws Exception {
        Session admin = login(mockMvc, "admin");
        Integer parent = json(send(admin, post("/api/v1/product-categories"),
                "{\"companyId\":1,\"code\":\"" + unique("C") + "\",\"name\":\"Cha\"}", newKey())
                .andExpect(status().isCreated()).andReturn(), "$.data.id");
        Integer child = json(send(admin, post("/api/v1/product-categories"),
                "{\"companyId\":1,\"code\":\"" + unique("C") + "\",\"name\":\"Con\",\"parentId\":" + parent + "}",
                newKey()).andExpect(status().isCreated()).andReturn(), "$.data.id");

        mockMvc.perform(as(admin, put("/api/v1/product-categories/" + parent)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Cha\",\"parentId\":" + child + ",\"version\":0}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("parentId"));
    }

    @Test
    @DisplayName("BR-SYS-04, BR-SYS-06: kho theo phạm vi chi nhánh / kho được gán; vị trí mặc định không ngừng được")
    void warehouseScope() throws Exception {
        Session warehouseStaff = login(mockMvc, "kho.hcm");
        MvcResult list = mockMvc.perform(as(warehouseStaff, get("/api/v1/warehouses")).param("companyId", "1"))
                .andExpect(status().isOk()).andReturn();
        List<String> codes = json(list, "$.data.items[*].code");
        assertThat(codes).contains("KHO_HCM").doesNotContain("KHO_HN");
        long hanoi = id("SELECT id FROM warehouses WHERE company_id = 1 AND code = 'KHO_HN'");
        mockMvc.perform(as(warehouseStaff, get("/api/v1/warehouses/" + hanoi))).andExpect(status().isNotFound());

        Session admin = login(mockMvc, "admin");
        long scoped = createUser(jdbc, "thukho.hn.kho", "WAREHOUSE_STAFF", "BRANCH", 2, 2, false);
        jdbc.update("DELETE FROM sys_user_data_scopes WHERE user_id = ?", scoped);
        mockMvc.perform(as(admin, put("/api/v1/users/" + scoped + "/data-scopes"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"dataScopes\":[{\"type\":\"WAREHOUSE\",\"scopeId\":" + hanoi + "}]}"))
                .andExpect(status().isOk());
        Session warehouseScoped = login(mockMvc, "thukho.hn.kho");
        MvcResult scopedList = mockMvc.perform(as(warehouseScoped, get("/api/v1/warehouses")).param("companyId", "1"))
                .andExpect(status().isOk()).andReturn();
        List<String> scopedCodes = json(scopedList, "$.data.items[*].code");
        assertThat(scopedCodes).containsExactly("KHO_HN");

        String code = unique("W");
        Integer warehouseId = json(send(admin, post("/api/v1/warehouses"), "{\"branchId\":1,\"code\":\"" + code
                + "\",\"name\":\"Kho test\",\"warehouseType\":\"NORMAL\"}", newKey())
                .andExpect(status().isCreated()).andReturn(), "$.data.id");
        MvcResult locations = mockMvc.perform(as(admin, get("/api/v1/warehouses/" + warehouseId + "/locations")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].defaultLocation").value(true))
                .andReturn();
        Integer locationId = json(locations, "$.data[0].id");
        mockMvc.perform(as(admin, patch("/api/v1/warehouse-locations/" + locationId + "/status"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"INACTIVE\",\"version\":0,\"reason\":\"Thử\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DATA_IN_USE"));
        send(admin, post("/api/v1/warehouses"), "{\"branchId\":1,\"code\":\"" + unique("W")
                + "\",\"name\":\"Kho ảo thiếu loại\",\"warehouseType\":\"VIRTUAL\"}", newKey())
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("BR-SYS-04: nhân viên kinh doanh chỉ thấy đối tác mình phụ trách; lọc theo vai trò")
    void partnerOwnData() throws Exception {
        Session admin = login(mockMvc, "admin");
        String own = unique("KH");
        String other = unique("KH");
        send(admin, post("/api/v1/partners"), partner(own, "3"), newKey()).andExpect(status().isCreated());
        Integer otherId = json(send(admin, post("/api/v1/partners"), partner(other, "null"), newKey())
                .andExpect(status().isCreated()).andReturn(), "$.data.id");

        Session sales = login(mockMvc, "kinhdoanh.hn");
        MvcResult list = mockMvc.perform(as(sales, get("/api/v1/partners")).param("companyId", "1")
                        .param("role", "CUSTOMER").param("size", "200"))
                .andExpect(status().isOk()).andReturn();
        List<String> codes = json(list, "$.data.items[*].code");
        assertThat(codes).contains(own).doesNotContain(other, "KH_BV01");
        mockMvc.perform(as(sales, get("/api/v1/partners/" + otherId))).andExpect(status().isNotFound());

        MvcResult suppliers = mockMvc.perform(as(admin, get("/api/v1/partners")).param("companyId", "1")
                        .param("role", "SUPPLIER"))
                .andExpect(status().isOk()).andReturn();
        List<String> supplierCodes = json(suppliers, "$.data.items[*].code");
        assertThat(supplierCodes).contains("NCC_01").doesNotContain(own);

        // Nhà cung cấp bắt buộc MST và địa chỉ (đơn mua lấy theo danh mục, không nhập tay)
        String supplier = partner(unique("NCC"), "null").replace("\"CUSTOMER\"", "\"SUPPLIER\"");
        send(admin, post("/api/v1/partners"), supplier, newKey()).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("taxCode"));
        send(admin, post("/api/v1/partners"), supplier.replace("\"roles\"", "\"taxCode\":\"0300000002\",\"roles\""),
                newKey()).andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").value("address"));
        send(admin, post("/api/v1/partners"), supplier.replace("\"roles\"",
                "\"taxCode\":\"0300000002\",\"address\":\"Hà Nội\",\"roles\""), newKey())
                .andExpect(status().isCreated()).andExpect(jsonPath("$.data.address").value("Hà Nội"));
    }

    @Test
    @DisplayName("BR-SYS-05: giá mua tham khảo ẩn với người không có quyền; đặt giá theo bậc số lượng")
    void priceLists() throws Exception {
        Session admin = login(mockMvc, "admin");
        Integer purchaseList = json(send(admin, post("/api/v1/price-lists"), priceList(unique("GM"),
                "PURCHASE_REFERENCE"), newKey()).andExpect(status().isCreated()).andReturn(), "$.data.id");
        Integer floorList = json(send(admin, post("/api/v1/price-lists"), priceList(unique("GS"), "FLOOR"), newKey())
                .andExpect(status().isCreated()).andReturn(), "$.data.id");

        long product = id("SELECT id FROM products WHERE company_id = 1 AND code = 'VT_ONG_01'");
        long box = id("SELECT id FROM units WHERE company_id = 1 AND code = 'HOP'");
        String item = "{\"productId\":" + product + ",\"unitId\":" + box + ",\"minQuantity\":10,\"unitPrice\":";
        mockMvc.perform(as(admin, put("/api/v1/price-lists/" + floorList + "/items"))
                .contentType(MediaType.APPLICATION_JSON).content(item + "150000}")).andExpect(status().isOk());
        mockMvc.perform(as(admin, put("/api/v1/price-lists/" + floorList + "/items"))
                        .contentType(MediaType.APPLICATION_JSON).content(item + "145000}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.unitPrice").value(145000));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM price_list_items WHERE price_list_id = ?", Integer.class,
                floorList)).isEqualTo(1);

        Session sales = login(mockMvc, "kinhdoanh.hn");
        mockMvc.perform(as(sales, get("/api/v1/price-lists/" + purchaseList))).andExpect(status().isNotFound());
        mockMvc.perform(as(sales, get("/api/v1/price-lists/" + floorList))).andExpect(status().isOk());
        MvcResult visible = mockMvc.perform(as(sales, get("/api/v1/price-lists")).param("companyId", "1")
                .param("size", "200")).andExpect(status().isOk()).andReturn();
        List<String> types = json(visible, "$.data.items[*].priceType");
        assertThat(types).doesNotContain("PURCHASE_REFERENCE");
    }

    @Test
    @DisplayName("BR-MD-44: hồ sơ có hạn bắt buộc ngày hết hạn; lọc sắp hết hạn; gắn lô chưa tồn tại bị từ chối")
    void complianceDocuments() throws Exception {
        Session admin = login(mockMvc, "admin");
        long type = id("SELECT id FROM compliance_document_types WHERE company_id = 1 AND code = 'SO_LH'");
        long lotType = id("SELECT id FROM compliance_document_types WHERE company_id = 1 AND code = 'CQ'");
        long product = id("SELECT id FROM products WHERE company_id = 1 AND code = 'MAY_XN_01'");
        String base = "{\"companyId\":1,\"documentTypeId\":" + type + ",\"ownerType\":\"PRODUCT\",\"ownerId\":" + product
                + ",\"documentNo\":\"" + unique("SLH") + "\"";

        send(admin, post("/api/v1/compliance-documents"), base + "}", newKey())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("expiryDate"));
        String expiry = LocalDate.now().plusDays(20).toString();
        Integer documentId = json(send(admin, post("/api/v1/compliance-documents"),
                base + ",\"expiryDate\":\"" + expiry + "\"}", newKey()).andExpect(status().isCreated()).andReturn(),
                "$.data.id");
        MvcResult expiring = mockMvc.perform(as(admin, get("/api/v1/compliance-documents")).param("companyId", "1")
                        .param("expiringWithinDays", "30").param("size", "200"))
                .andExpect(status().isOk()).andReturn();
        List<Integer> ids = json(expiring, "$.data.items[*].id");
        assertThat(ids).contains(documentId);

        send(admin, post("/api/v1/compliance-documents"), "{\"companyId\":1,\"documentTypeId\":" + lotType
                + ",\"ownerType\":\"LOT\",\"ownerId\":999999999,\"documentNo\":\"CQ-1\"}", newKey())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("ownerId"));
    }

    @Test
    @DisplayName("BR-UX-05: nhập hàng hóa từ Excel tất cả hoặc không; lỗi báo theo dòng; file sai định dạng bị từ chối")
    void productImport() throws Exception {
        Session admin = login(mockMvc, "admin");
        mockMvc.perform(as(admin, get("/api/v1/products/import-template"))).andExpect(status().isOk());
        String first = unique("IMP");
        String second = unique("IMP");

        importFile(admin, workbook(List.of(row(first, "VT", "VT_CAI"), row(second, "XX_KHONG_CO", "CONSUMABLE"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("rows[2].productType"));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM products WHERE code IN (?, ?)", Integer.class, first,
                second)).isZero();

        importFile(admin, workbook(List.of(row(first, "VT", "CONSUMABLE"), row(second, "VT", "CONSUMABLE"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.created").value(2));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM products WHERE code IN (?, ?) AND lot_tracked = TRUE",
                Integer.class, first, second)).isEqualTo(2);

        mockMvc.perform(as(admin, multipart("/api/v1/products/import")
                        .file(new MockMultipartFile("file", "a.xlsx", "application/octet-stream", "not excel".getBytes())))
                        .param("companyId", "1").header(IdempotentCommand.HEADER, newKey()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("file"));
    }

    private ResultActions importFile(Session session, byte[] content) throws Exception {
        return mockMvc.perform(as(session, multipart("/api/v1/products/import")
                        .file(new MockMultipartFile("file", "hang.xlsx", "application/octet-stream", content)))
                .param("companyId", "1").header(IdempotentCommand.HEADER, newKey()));
    }

    private static String[] row(String code, String category, String type) {
        return new String[] {code, "", "Hàng nhập " + code, "", category, type, "CAI", "", "", "VN", "", "", "C", "C"};
    }

    private static byte[] workbook(List<String[]> rows) throws Exception {
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("Hang hoa");
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

    private static String product(String code, String type, long categoryId, long unitId, boolean lot, boolean expiry,
                                  boolean serial) {
        return "{\"companyId\":1,\"code\":\"" + code + "\",\"name\":\"Hàng " + code + "\",\"categoryId\":" + categoryId
                + ",\"productType\":\"" + type + "\",\"baseUnitId\":" + unitId + ",\"lotTracked\":" + lot
                + ",\"expiryTracked\":" + expiry + ",\"serialTracked\":" + serial + "}";
    }

    private static String partner(String code, String salesUserId) {
        return "{\"companyId\":1,\"code\":\"" + code + "\",\"name\":\"Khách " + code
                + "\",\"partnerKind\":\"ORGANIZATION\",\"roles\":[\"CUSTOMER\"],\"salesUserId\":" + salesUserId
                + ",\"contacts\":[{\"name\":\"Người liên hệ\",\"primaryContact\":true}]}";
    }

    private static String priceList(String code, String type) {
        return "{\"companyId\":1,\"code\":\"" + code + "\",\"name\":\"Bảng giá " + code + "\",\"priceType\":\"" + type
                + "\",\"targetType\":\"ALL\",\"validFrom\":\"2026-01-01\"}";
    }

    private long id(String sql) {
        return jdbc.queryForObject(sql, Long.class);
    }

    private ResultActions send(Session session, MockHttpServletRequestBuilder request, String body, String key)
            throws Exception {
        return mockMvc.perform(as(session, request).header(IdempotentCommand.HEADER, key)
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private static MockHttpServletRequestBuilder as(Session session, MockHttpServletRequestBuilder request) {
        return request.header(HttpHeaders.AUTHORIZATION, session.bearer());
    }
}
