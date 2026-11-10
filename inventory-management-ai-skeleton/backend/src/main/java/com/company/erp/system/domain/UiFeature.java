package com.company.erp.system.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.Immutable;

/**
 * Cấu hình ẩn / đổi tên một menu hoặc nút (D-72). Chỉ sửa bằng SQL tại khách hàng; ứng dụng chỉ đọc. Ẩn chỉ phục vụ
 * giao diện, không thay phân quyền.
 */
@Entity
@Immutable
@Table(name = "sys_ui_features")
public class UiFeature {

    @Id
    @Column(name = "feature_key", length = 150)
    private String key;

    @Column(name = "enabled", nullable = false)
    private boolean enabled;

    @Column(name = "custom_label", length = 150)
    private String customLabel;

    protected UiFeature() {
    }

    public String getKey() {
        return key;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public String getCustomLabel() {
        return customLabel;
    }
}
