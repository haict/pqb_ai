package com.company.erp.system.domain;

import com.company.erp.common.domain.AuditableEntity;
import com.company.erp.common.domain.RecordStatus;
import jakarta.persistence.CascadeType;
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
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Người dùng (BR-SYS-02). Mật khẩu chỉ lưu dạng băm; tên đăng nhập không đổi sau khi tạo. */
@Entity
@Table(name = "sys_users")
public class User extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(name = "default_branch_id")
    private Long defaultBranchId;

    @Column(name = "username", nullable = false, length = 50, updatable = false)
    private String username;

    @Column(name = "full_name", nullable = false, length = 150)
    private String fullName;

    @Column(name = "email", length = 150)
    private String email;

    @Column(name = "phone", length = 30)
    private String phone;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private RecordStatus status;

    /** Nhân viên chỉ thấy dữ liệu của mình (BR-SYS-04); áp dụng ở module nghiệp vụ. */
    @Column(name = "own_data_only", nullable = false)
    private boolean ownDataOnly;

    @Column(name = "must_change_password", nullable = false)
    private boolean mustChangePassword;

    // Thống kê đăng nhập chỉ cập nhật bằng câu lệnh riêng (UserRepository.record*), không qua entity:
    // đăng nhập không làm tăng version, không ghi đè updated_by/updated_at của người sửa cuối.
    @Column(name = "failed_login_count", nullable = false, updatable = false)
    private int failedLoginCount;

    @Column(name = "locked_until", updatable = false)
    private Instant lockedUntil;

    /**
     * Mốc đổi thông tin đăng nhập (độ chính xác mili-giây, khớp DATETIME(3)); nhúng vào access token,
     * token mang giá trị khác bị từ chối (đổi/đặt lại mật khẩu).
     */
    @Column(name = "credentials_changed_at", nullable = false)
    private Instant credentialsChangedAt;

    @Column(name = "last_login_at", updatable = false)
    private Instant lastLoginAt;

    @ManyToMany
    @JoinTable(name = "sys_user_roles",
            joinColumns = @JoinColumn(name = "user_id"),
            inverseJoinColumns = @JoinColumn(name = "role_id"))
    private Set<Role> roles = new HashSet<>();

    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<UserDataScope> dataScopes = new ArrayList<>();

    protected User() {
    }

    public User(Long companyId, Long defaultBranchId, String username, String fullName, String email, String phone,
                String passwordHash, boolean ownDataOnly, Instant now) {
        this.companyId = companyId;
        this.username = username;
        this.passwordHash = passwordHash;
        this.status = RecordStatus.ACTIVE;
        this.mustChangePassword = true;
        this.credentialsChangedAt = now.truncatedTo(ChronoUnit.MILLIS);
        updateProfile(defaultBranchId, fullName, email, phone, ownDataOnly);
    }

    public void updateProfile(Long defaultBranchId, String fullName, String email, String phone, boolean ownDataOnly) {
        this.defaultBranchId = defaultBranchId;
        this.fullName = fullName;
        this.email = email;
        this.phone = phone;
        this.ownDataOnly = ownDataOnly;
    }

    public boolean isLockedAt(Instant now) {
        return lockedUntil != null && lockedUntil.isAfter(now);
    }

    public boolean isActive() {
        return status == RecordStatus.ACTIVE;
    }

    /**
     * Trạng thái sau một lần đăng nhập sai: đủ số lần cho phép thì khóa tạm (BR-SYS-23).
     * Đang bị khóa thì giữ nguyên, không kéo dài thời gian khóa.
     */
    public FailedLoginState afterFailedLogin(Instant now, int maxFailedAttempts, Duration lockDuration) {
        if (isLockedAt(now)) {
            return new FailedLoginState(failedLoginCount, lockedUntil, false);
        }
        int failures = failedLoginCount + 1;
        if (failures >= maxFailedAttempts) {
            return new FailedLoginState(0, now.plus(lockDuration), true);
        }
        return new FailedLoginState(failures, null, false);
    }

    public void changePassword(String newPasswordHash, Instant now, boolean mustChangeOnNextLogin) {
        this.passwordHash = newPasswordHash;
        this.credentialsChangedAt = now.truncatedTo(ChronoUnit.MILLIS);
        this.mustChangePassword = mustChangeOnNextLogin;
    }

    public void changeStatus(RecordStatus status) {
        this.status = status;
    }

    public void replaceRoles(Collection<Role> newRoles) {
        roles.clear();
        roles.addAll(newRoles);
    }

    public void addDataScope(ScopeAssignment assignment) {
        boolean exists = dataScopes.stream().anyMatch(scope ->
                scope.getScopeType() == assignment.type() && scope.getScopeId().equals(assignment.scopeId()));
        if (!exists) {
            dataScopes.add(new UserDataScope(this, assignment.type(), assignment.scopeId()));
        }
    }

    public void replaceDataScopes(Collection<ScopeAssignment> assignments) {
        dataScopes.clear();
        for (ScopeAssignment assignment : Set.copyOf(assignments)) {
            dataScopes.add(new UserDataScope(this, assignment.type(), assignment.scopeId()));
        }
    }

    public Long getId() {
        return id;
    }

    public Long getCompanyId() {
        return companyId;
    }

    public Long getDefaultBranchId() {
        return defaultBranchId;
    }

    public String getUsername() {
        return username;
    }

    public String getFullName() {
        return fullName;
    }

    public String getEmail() {
        return email;
    }

    public String getPhone() {
        return phone;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public RecordStatus getStatus() {
        return status;
    }

    public boolean isOwnDataOnly() {
        return ownDataOnly;
    }

    public boolean isMustChangePassword() {
        return mustChangePassword;
    }

    public Instant getLockedUntil() {
        return lockedUntil;
    }

    public Instant getCredentialsChangedAt() {
        return credentialsChangedAt;
    }

    /** Giá trị nhúng vào access token để vô hiệu hóa token cũ khi đổi mật khẩu. */
    public long credentialsVersion() {
        return credentialsChangedAt.toEpochMilli();
    }

    public Instant getLastLoginAt() {
        return lastLoginAt;
    }

    public Set<Role> getRoles() {
        return Set.copyOf(roles);
    }

    public List<UserDataScope> getDataScopes() {
        return List.copyOf(dataScopes);
    }

    /** Kết quả đánh giá một lần đăng nhập sai. */
    public record FailedLoginState(int failedLoginCount, Instant lockedUntil, boolean lockedNow) {
    }

    /** Một phạm vi dữ liệu cần gán. */
    public record ScopeAssignment(DataScopeType type, Long scopeId) {
    }
}
