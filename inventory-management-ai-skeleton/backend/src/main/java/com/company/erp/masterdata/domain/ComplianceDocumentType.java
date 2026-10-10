package com.company.erp.masterdata.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Loại hồ sơ / giấy phép (BR-MD-44, D-28): số lưu hành, CO, CQ, ủy quyền hãng... có hạn hay không, gắn với đối tượng
 * nào, bắt buộc với loại hàng nào. `[Chưa kiểm chứng]` Danh mục pháp lý bắt buộc do pháp chế rà soát.
 */
@Entity
@Table(name = "compliance_document_types")
public class ComplianceDocumentType extends CodeNameEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "applies_to", nullable = false, length = 20)
    private ComplianceOwnerType appliesTo;

    @Column(name = "has_expiry", nullable = false)
    private boolean hasExpiry;

    /** Danh sách loại hàng bắt buộc có hồ sơ này, phân tách dấu phẩy. */
    @Column(name = "required_product_types", length = 200)
    private String requiredProductTypes;

    protected ComplianceDocumentType() {
    }

    public ComplianceDocumentType(Long companyId, String code, String name, ComplianceOwnerType appliesTo,
                                  boolean hasExpiry, Set<ProductType> requiredProductTypes) {
        super(companyId, code, name);
        update(name, appliesTo, hasExpiry, requiredProductTypes);
    }

    public final void update(String name, ComplianceOwnerType appliesTo, boolean hasExpiry,
                             Set<ProductType> requiredProductTypes) {
        rename(name);
        this.appliesTo = appliesTo;
        this.hasExpiry = hasExpiry;
        this.requiredProductTypes = requiredProductTypes == null || requiredProductTypes.isEmpty() ? null
                : requiredProductTypes.stream().map(Enum::name).sorted().collect(Collectors.joining(","));
    }

    public Set<ProductType> requiredProductTypeSet() {
        if (requiredProductTypes == null || requiredProductTypes.isBlank()) {
            return Set.of();
        }
        return Arrays.stream(requiredProductTypes.split(","))
                .map(ProductType::valueOf)
                .collect(Collectors.toCollection(() -> EnumSet.noneOf(ProductType.class)));
    }

    @Override
    public Long getId() {
        return id;
    }

    public ComplianceOwnerType getAppliesTo() {
        return appliesTo;
    }

    public boolean isHasExpiry() {
        return hasExpiry;
    }
}
