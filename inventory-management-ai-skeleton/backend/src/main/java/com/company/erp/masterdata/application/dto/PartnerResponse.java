package com.company.erp.masterdata.application.dto;

import com.company.erp.common.domain.RecordStatus;
import com.company.erp.masterdata.domain.CustomerType;
import com.company.erp.masterdata.domain.PartnerKind;
import com.company.erp.masterdata.domain.PartnerRole;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Set;

/** Đối tác; {@code contacts}, {@code addresses} chỉ có ở API chi tiết. */
public record PartnerResponse(
        Long id,
        Long companyId,
        String code,
        String legacyCode,
        String name,
        String shortName,
        PartnerKind partnerKind,
        String taxCode,
        String address,
        String phone,
        String email,
        String countryCode,
        Long partnerGroupId,
        Set<PartnerRole> roles,
        CustomerType customerType,
        Long salesUserId,
        Long managingBranchId,
        Integer paymentTermDays,
        BigDecimal creditLimit,
        Integer minRemainingShelfLifeDays,
        Long priceListId,
        Integer supplierPaymentTermDays,
        String currencyCode,
        String distributedBrands,
        BigDecimal agentDiscountPercent,
        String agentRegion,
        BigDecimal agentCreditLimit,
        List<PartnerContactDto> contacts,
        List<PartnerAddressDto> addresses,
        RecordStatus status,
        int version,
        Instant updatedAt) {
}
