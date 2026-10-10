package com.company.erp.masterdata.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Nhóm đối tác, dùng cho bảng giá theo nhóm khách (BR-MD-31, BR-MD-42). */
@Entity
@Table(name = "partner_groups")
public class PartnerGroup extends CodeNameEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    protected PartnerGroup() {
    }

    public PartnerGroup(Long companyId, String code, String name) {
        super(companyId, code, name);
    }

    @Override
    public Long getId() {
        return id;
    }
}
