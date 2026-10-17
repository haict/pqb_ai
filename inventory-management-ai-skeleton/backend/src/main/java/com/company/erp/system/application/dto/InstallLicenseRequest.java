package com.company.erp.system.application.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Nạp license (D-59): nguyên văn nội dung file license do nhà cung cấp gửi. */
public record InstallLicenseRequest(@NotBlank @Size(max = 10000) String license) {
}
