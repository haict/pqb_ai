package com.company.erp.system.application.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public record AssignDataScopesRequest(@NotNull @Valid List<DataScopeDto> dataScopes) {
}
