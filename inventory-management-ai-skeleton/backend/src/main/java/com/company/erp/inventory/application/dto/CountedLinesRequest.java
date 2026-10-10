package com.company.erp.inventory.application.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

public record CountedLinesRequest(@NotEmpty @Size(max = 2000) List<@Valid @NotNull CountedLineRequest> lines) {
}
