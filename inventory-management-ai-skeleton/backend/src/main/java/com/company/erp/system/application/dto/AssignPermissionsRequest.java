package com.company.erp.system.application.dto;

import jakarta.validation.constraints.NotNull;
import java.util.Set;

public record AssignPermissionsRequest(@NotNull Set<String> permissionCodes) {
}
