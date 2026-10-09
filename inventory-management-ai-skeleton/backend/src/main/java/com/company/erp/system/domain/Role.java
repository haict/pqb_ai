package com.company.erp.system.domain;

import com.company.erp.common.domain.AuditableEntity;
import com.company.erp.common.domain.RecordStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.Table;
import java.util.HashSet;
import java.util.Set;

/** Vai trò = tập quyền (BR-SYS-02, BR-SYS-03). Vai trò hệ thống không đổi mã. */
@Entity
@Table(name = "sys_roles")
public class Role extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "code", nullable = false, length = 50, updatable = false)
    private String code;

    @Column(name = "name", nullable = false, length = 150)
    private String name;

    @Column(name = "description", length = 500)
    private String description;

    @Column(name = "system_role", nullable = false, updatable = false)
    private boolean systemRole;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private RecordStatus status;

    @ManyToMany
    @JoinTable(name = "sys_role_permissions",
            joinColumns = @JoinColumn(name = "role_id"),
            inverseJoinColumns = @JoinColumn(name = "permission_id"))
    private Set<Permission> permissions = new HashSet<>();

    protected Role() {
    }

    public Role(String code, String name, String description) {
        this.code = code;
        this.name = name;
        this.description = description;
        this.systemRole = false;
        this.status = RecordStatus.ACTIVE;
    }

    public void update(String name, String description) {
        this.name = name;
        this.description = description;
    }

    public void changeStatus(RecordStatus status) {
        this.status = status;
    }

    public void replacePermissions(Set<Permission> newPermissions) {
        permissions.clear();
        permissions.addAll(newPermissions);
    }

    public Long getId() {
        return id;
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public boolean isSystemRole() {
        return systemRole;
    }

    public RecordStatus getStatus() {
        return status;
    }

    public Set<Permission> getPermissions() {
        return Set.copyOf(permissions);
    }
}
