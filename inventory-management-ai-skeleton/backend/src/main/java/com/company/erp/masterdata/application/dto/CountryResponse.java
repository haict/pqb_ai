package com.company.erp.masterdata.application.dto;

import com.company.erp.common.domain.RecordStatus;

public record CountryResponse(String code, String name, RecordStatus status, int version) {
}
