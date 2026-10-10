package com.company.erp.masterdata.domain;

import java.math.BigDecimal;
import java.util.Set;

/** Thông tin sửa được của đối tác, gồm điều khoản theo vai trò (BR-MD-30..35). */
public record PartnerDetails(
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
        BigDecimal agentCreditLimit) {
}
