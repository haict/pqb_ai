package com.company.erp.masterdata.application;

import com.company.erp.common.domain.BusinessException;
import java.util.Arrays;

/** Danh mục dạng mã - tên dùng chung một bộ API ({@code /api/v1/{path}}). */
public enum SimpleCatalog {
    UNITS("units", "UNIT", "Đơn vị tính"),
    BRANDS("brands", "BRAND", "Nhãn hiệu"),
    MANUFACTURERS("manufacturers", "MANUFACTURER", "Nhà sản xuất"),
    PARTNER_GROUPS("partner-groups", "PARTNER_GROUP", "Nhóm đối tác");

    /** Biểu thức đường dẫn cho controller. */
    public static final String PATH_PATTERN = "units|brands|manufacturers|partner-groups";

    private final String path;
    private final String auditType;
    private final String label;

    SimpleCatalog(String path, String auditType, String label) {
        this.path = path;
        this.auditType = auditType;
        this.label = label;
    }

    public static SimpleCatalog fromPath(String path) {
        return Arrays.stream(values()).filter(catalog -> catalog.path.equals(path)).findFirst()
                .orElseThrow(BusinessException::notFound);
    }

    public String path() {
        return path;
    }

    public String auditType() {
        return auditType;
    }

    public String label() {
        return label;
    }
}
