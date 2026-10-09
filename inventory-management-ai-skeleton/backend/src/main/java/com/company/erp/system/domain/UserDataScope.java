package com.company.erp.system.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "sys_user_data_scopes")
public class UserDataScope {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(name = "scope_type", nullable = false, length = 20)
    private DataScopeType scopeType;

    @Column(name = "scope_id", nullable = false)
    private Long scopeId;

    protected UserDataScope() {
    }

    UserDataScope(User user, DataScopeType scopeType, Long scopeId) {
        this.user = user;
        this.scopeType = scopeType;
        this.scopeId = scopeId;
    }

    public DataScopeType getScopeType() {
        return scopeType;
    }

    public Long getScopeId() {
        return scopeId;
    }
}
