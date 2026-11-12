package com.company.erp.masterdata;

import static org.assertj.core.api.Assertions.assertThat;

import com.company.erp.masterdata.domain.WarehouseNameMatcher;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** D-76: gợi ý kho theo tên nhóm hàng gần đúng. */
class WarehouseNameMatcherTest {

    @Test
    @DisplayName("D-76: khóa tên bỏ dấu, chữ hoa, ký tự đặc biệt và chữ 'kho'")
    void key() {
        assertThat(WarehouseNameMatcher.key("Kho ĐÔNG MÁU - HN")).isEqualTo("dong mau hn");
        assertThat(WarehouseNameMatcher.key("Đông máu")).isEqualTo("dong mau");
        assertThat(WarehouseNameMatcher.key(null)).isEmpty();
    }

    @Test
    @DisplayName("D-76: ưu tiên trùng hẳn, rồi tên kho chứa tên nhóm, rồi tên nhóm chứa tên kho; nhiều kho cùng mức thì không chọn")
    void match() {
        Map<Long, String> warehouses = new LinkedHashMap<>();
        warehouses.put(1L, "Kho chính");
        warehouses.put(2L, "Kho đông máu");
        warehouses.put(3L, "Kho hóa chất đông máu lạnh");
        warehouses.put(4L, "Kho sinh hóa");
        warehouses.put(5L, "Kho sinh hóa 2");
        assertThat(WarehouseNameMatcher.match("Đông máu", warehouses)).contains(2L);
        assertThat(WarehouseNameMatcher.match("Hóa chất đông máu", warehouses)).contains(3L);
        assertThat(WarehouseNameMatcher.match("Thuốc thử đông máu nhanh", warehouses)).contains(2L);
        assertThat(WarehouseNameMatcher.match("Sinh hóa", warehouses)).contains(4L);
        assertThat(WarehouseNameMatcher.match("Hóa", warehouses)).isEmpty();
        assertThat(WarehouseNameMatcher.match("Máu", warehouses)).isEmpty();
        assertThat(WarehouseNameMatcher.match("Miễn dịch", warehouses)).isEmpty();
        assertThat(WarehouseNameMatcher.match("Kho", warehouses)).isEmpty();
    }
}
