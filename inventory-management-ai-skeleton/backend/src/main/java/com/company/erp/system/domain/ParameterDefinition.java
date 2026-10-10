package com.company.erp.system.domain;

import com.company.erp.common.domain.BusinessException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import org.hibernate.annotations.Immutable;

/**
 * Định nghĩa tham số hệ thống (BR-SYS-13): kiểu, giới hạn, giá trị mặc định. Dữ liệu tham chiếu do migration
 * của module dùng tham số thêm vào; ứng dụng chỉ đọc.
 */
@Entity
@Immutable
@Table(name = "sys_parameter_definitions")
public class ParameterDefinition {

    private static final String VALUE_FIELD = "value";
    private static final int MAX_LENGTH = 500;

    @Id
    @Column(name = "param_key", length = 80)
    private String key;

    @Column(name = "module", nullable = false, length = 30)
    private String module;

    @Column(name = "name", nullable = false, length = 200)
    private String name;

    @Column(name = "description", length = 500)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "value_type", nullable = false, length = 20)
    private ParameterValueType valueType;

    @Column(name = "default_value", nullable = false, length = 500)
    private String defaultValue;

    @Column(name = "min_value", precision = 19, scale = 4)
    private BigDecimal minValue;

    @Column(name = "max_value", precision = 19, scale = 4)
    private BigDecimal maxValue;

    @Column(name = "allowed_values", length = 500)
    private String allowedValues;

    @Column(name = "lock_after_posting", nullable = false)
    private boolean lockAfterPosting;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    protected ParameterDefinition() {
    }

    /** Kiểm tra và chuẩn hóa giá trị nhập theo kiểu và giới hạn; sai thì báo lỗi trên trường {@code value}. */
    public String normalize(String rawValue) {
        String value = rawValue == null ? "" : rawValue.trim();
        if (value.isEmpty() || value.length() > MAX_LENGTH) {
            throw BusinessException.invalidField(VALUE_FIELD, "Giá trị bắt buộc, tối đa " + MAX_LENGTH + " ký tự.");
        }
        return switch (valueType) {
            case INTEGER -> checkRange(parseNumber(value, true)).toPlainString();
            case DECIMAL -> checkRange(parseNumber(value, false)).stripTrailingZeros().toPlainString();
            case BOOLEAN -> parseBoolean(value);
            case ENUM -> checkAllowed(value);
            case STRING -> value;
        };
    }

    private BigDecimal parseNumber(String value, boolean integer) {
        try {
            BigDecimal number = new BigDecimal(value);
            if (integer) {
                return BigDecimal.valueOf(number.intValueExact());
            }
            return number;
        } catch (NumberFormatException | ArithmeticException ex) {
            throw BusinessException.invalidField(VALUE_FIELD, integer ? "Phải là số nguyên." : "Phải là số.");
        }
    }

    private BigDecimal checkRange(BigDecimal number) {
        if (minValue != null && number.compareTo(minValue) < 0) {
            throw BusinessException.invalidField(VALUE_FIELD, "Không nhỏ hơn " + minValue.stripTrailingZeros().toPlainString() + ".");
        }
        if (maxValue != null && number.compareTo(maxValue) > 0) {
            throw BusinessException.invalidField(VALUE_FIELD, "Không lớn hơn " + maxValue.stripTrailingZeros().toPlainString() + ".");
        }
        return number;
    }

    private static String parseBoolean(String value) {
        String lower = value.toLowerCase(Locale.ROOT);
        if (!lower.equals("true") && !lower.equals("false")) {
            throw BusinessException.invalidField(VALUE_FIELD, "Phải là true hoặc false.");
        }
        return lower;
    }

    private String checkAllowed(String value) {
        if (!allowedValueList().contains(value)) {
            throw BusinessException.invalidField(VALUE_FIELD, "Giá trị không nằm trong danh sách cho phép.");
        }
        return value;
    }

    public List<String> allowedValueList() {
        if (allowedValues == null || allowedValues.isBlank()) {
            return List.of();
        }
        return Arrays.stream(allowedValues.split(",")).map(String::trim).filter(v -> !v.isEmpty()).toList();
    }

    public String getKey() {
        return key;
    }

    public String getModule() {
        return module;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public ParameterValueType getValueType() {
        return valueType;
    }

    public String getDefaultValue() {
        return defaultValue;
    }

    public BigDecimal getMinValue() {
        return minValue;
    }

    public BigDecimal getMaxValue() {
        return maxValue;
    }

    public boolean isLockAfterPosting() {
        return lockAfterPosting;
    }

    public int getSortOrder() {
        return sortOrder;
    }
}
