package com.company.erp.system.application.dto;

import com.company.erp.system.domain.DataScopeType;
import jakarta.validation.constraints.NotNull;

public record DataScopeDto(@NotNull DataScopeType type, @NotNull Long scopeId) {
}
