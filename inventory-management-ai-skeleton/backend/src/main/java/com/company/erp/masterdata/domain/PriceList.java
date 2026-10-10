package com.company.erp.masterdata.domain;

import com.company.erp.common.domain.BusinessException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;

/** Bảng giá có hiệu lực theo thời gian, áp dụng cho mọi khách / nhóm / một đối tác (BR-MD-42). */
@Entity
@Table(name = "price_lists")
public class PriceList extends CodeNameEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "price_type", nullable = false, length = 30, updatable = false)
    private PriceType priceType;

    @Enumerated(EnumType.STRING)
    @Column(name = "target_type", nullable = false, length = 20)
    private PriceTargetType targetType;

    @Column(name = "partner_group_id")
    private Long partnerGroupId;

    @Column(name = "partner_id")
    private Long partnerId;

    @Column(name = "currency_code", nullable = false, length = 3)
    private String currencyCode;

    @Column(name = "valid_from", nullable = false)
    private LocalDate validFrom;

    @Column(name = "valid_to")
    private LocalDate validTo;

    protected PriceList() {
    }

    public PriceList(Long companyId, String code, String name, PriceType priceType, PriceTargetType targetType,
                     Long partnerGroupId, Long partnerId, String currencyCode, LocalDate validFrom, LocalDate validTo) {
        super(companyId, code, name);
        this.priceType = priceType;
        update(name, targetType, partnerGroupId, partnerId, currencyCode, validFrom, validTo);
    }

    public final void update(String name, PriceTargetType targetType, Long partnerGroupId, Long partnerId,
                             String currencyCode, LocalDate validFrom, LocalDate validTo) {
        boolean validTarget = switch (targetType) {
            case ALL -> partnerGroupId == null && partnerId == null;
            case PARTNER_GROUP -> partnerGroupId != null && partnerId == null;
            case PARTNER -> partnerId != null && partnerGroupId == null;
        };
        if (!validTarget) {
            throw BusinessException.invalidField("targetType", "Đối tượng áp dụng không khớp nhóm / đối tác đã chọn.");
        }
        if (validTo != null && validTo.isBefore(validFrom)) {
            throw BusinessException.invalidField("validTo", "Ngày kết thúc phải sau ngày bắt đầu.");
        }
        rename(name);
        this.targetType = targetType;
        this.partnerGroupId = partnerGroupId;
        this.partnerId = partnerId;
        this.currencyCode = currencyCode;
        this.validFrom = validFrom;
        this.validTo = validTo;
    }

    @Override
    public Long getId() {
        return id;
    }

    public PriceType getPriceType() {
        return priceType;
    }

    public PriceTargetType getTargetType() {
        return targetType;
    }

    public Long getPartnerGroupId() {
        return partnerGroupId;
    }

    public Long getPartnerId() {
        return partnerId;
    }

    public String getCurrencyCode() {
        return currencyCode;
    }

    public LocalDate getValidFrom() {
        return validFrom;
    }

    public LocalDate getValidTo() {
        return validTo;
    }
}
