package com.company.erp.system.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.Immutable;

/** Thiết lập ứng dụng dạng khóa / giá trị (D-72), sửa bằng SQL tại khách hàng; {@code null} = mặc định. */
@Entity
@Immutable
@Table(name = "sys_app_settings")
public class AppSetting {

    public static final String APP_NAME = "branding.app-name";
    public static final String LOGO_FILE = "branding.logo-file";
    public static final String POSTING_MODE = "posting.mode";

    @Id
    @Column(name = "setting_key", length = 80)
    private String key;

    @Column(name = "setting_value", length = 500)
    private String value;

    protected AppSetting() {
    }

    public String getKey() {
        return key;
    }

    public String getValue() {
        return value;
    }
}
