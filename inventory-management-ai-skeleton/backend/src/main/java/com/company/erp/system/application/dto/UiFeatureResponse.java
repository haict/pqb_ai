package com.company.erp.system.application.dto;

/** Cấu hình một menu / nút (D-72); {@code customLabel = null}: giữ tên mặc định. */
public record UiFeatureResponse(String key, boolean enabled, String customLabel) {
}
