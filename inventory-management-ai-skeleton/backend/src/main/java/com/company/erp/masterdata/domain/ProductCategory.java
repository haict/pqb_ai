package com.company.erp.masterdata.domain;

import jakarta.persistence.AttributeOverride;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Nhóm hàng dạng cây (BR-MD-03). Kiểm tra vòng lặp cha - con ở tầng application. Hàng và nhóm con tham chiếu theo
 * ID nên mã sửa được (D-67).
 */
@Entity
@Table(name = "product_categories")
@AttributeOverride(name = "code", column = @Column(name = "code", nullable = false))
public class ProductCategory extends CodeNameEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "parent_id")
    private Long parentId;

    protected ProductCategory() {
    }

    public ProductCategory(Long companyId, String code, String name, Long parentId) {
        super(companyId, code, name);
        this.parentId = parentId;
    }

    public void update(String code, String name, Long parentId) {
        recode(code);
        rename(name);
        this.parentId = parentId;
    }

    @Override
    public Long getId() {
        return id;
    }

    public Long getParentId() {
        return parentId;
    }
}
