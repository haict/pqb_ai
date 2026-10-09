package com.company.erp.system.application.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ResetPasswordRequest(@NotBlank @Size(max = 200) String newPassword) {

    @Override
    public String toString() {
        return "ResetPasswordRequest[***]";
    }
}
