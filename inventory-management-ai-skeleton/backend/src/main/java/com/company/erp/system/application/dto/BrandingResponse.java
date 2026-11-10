package com.company.erp.system.application.dto;

/**
 * Nhận diện thương hiệu công khai cho trang đăng nhập (D-72). {@code appName = null}: tên mặc định;
 * {@code logoVersion = null}: logo mặc định, ngược lại tải {@code /public/branding/logo?v=logoVersion}.
 */
public record BrandingResponse(String appName, String logoVersion) {
}
