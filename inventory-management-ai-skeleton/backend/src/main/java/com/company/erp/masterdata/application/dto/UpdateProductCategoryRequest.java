package com.company.erp.masterdata.application.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record UpdateProductCategoryRequest(
        @NotBlank @Size(max = 200) String name,
        Long parentId,
        @NotNull Integer version) {
}
