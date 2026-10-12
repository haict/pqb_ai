package com.company.erp.accounting.domain;

import com.company.erp.common.domain.AuditableEntity;
import com.company.erp.common.domain.BusinessException;
import com.company.erp.common.domain.RecordStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Tài khoản kế toán dạng cây (BR-ACC-01). Mã không đổi, mã tài khoản con bắt đầu bằng mã tài khoản cha. Chỉ tài
 * khoản không có tài khoản con (chi tiết) được hạch toán; ngừng hoạt động thay vì xóa.
 */
@Entity
@Table(name = "chart_of_accounts")
public class Account extends AuditableEntity {

    public static final int MAX_LEVEL = 10;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "company_id", nullable = false, updatable = false)
    private Long companyId;

    @Column(name = "code", nullable = false, updatable = false)
    private String code;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "parent_id", updatable = false)
    private Long parentId;

    @Column(name = "account_level", nullable = false, updatable = false)
    private int level;

    @Enumerated(EnumType.STRING)
    @Column(name = "balance_side", nullable = false)
    private BalanceSide balanceSide;

    @Column(name = "postable", nullable = false)
    private boolean postable;

    @Column(name = "partner_required", nullable = false)
    private boolean partnerRequired;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private RecordStatus status;

    protected Account() {
    }

    public Account(Long companyId, String code, String name, Account parent, BalanceSide balanceSide,
                   boolean partnerRequired) {
        if (parent != null) {
            requireValidParent(companyId, code, parent);
        }
        this.companyId = companyId;
        this.code = code;
        this.parentId = parent == null ? null : parent.getId();
        this.level = parent == null ? 1 : parent.getLevel() + 1;
        this.postable = true;
        this.status = RecordStatus.ACTIVE;
        update(name, balanceSide, partnerRequired);
    }

    private static void requireValidParent(Long companyId, String code, Account parent) {
        if (!parent.getCompanyId().equals(companyId)) {
            throw BusinessException.invalidField("parentId", "Tài khoản cha không thuộc công ty.");
        }
        if (!code.startsWith(parent.getCode()) || code.equals(parent.getCode())) {
            throw BusinessException.invalidField("code", "Mã tài khoản con phải bắt đầu bằng mã tài khoản cha "
                    + parent.getCode() + " và dài hơn mã cha.");
        }
        if (parent.getLevel() >= MAX_LEVEL) {
            throw BusinessException.invalidField("parentId", "Tài khoản tối đa " + MAX_LEVEL + " cấp.");
        }
        if (!parent.isActive()) {
            throw BusinessException.invalidField("parentId", "Tài khoản cha " + parent.getCode()
                    + " đã ngừng hoạt động.");
        }
    }

    public final void update(String name, BalanceSide balanceSide, boolean partnerRequired) {
        this.name = name;
        this.balanceSide = balanceSide;
        this.partnerRequired = partnerRequired;
    }

    /** Có tài khoản con thì trở thành tài khoản tổng hợp, không hạch toán trực tiếp. */
    public void markHasChildren() {
        this.postable = false;
    }

    public void changeStatus(RecordStatus status) {
        this.status = status;
    }

    public boolean isActive() {
        return status == RecordStatus.ACTIVE;
    }

    public Long getId() {
        return id;
    }

    public Long getCompanyId() {
        return companyId;
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public Long getParentId() {
        return parentId;
    }

    public int getLevel() {
        return level;
    }

    public BalanceSide getBalanceSide() {
        return balanceSide;
    }

    public boolean isPostable() {
        return postable;
    }

    public boolean isPartnerRequired() {
        return partnerRequired;
    }

    public RecordStatus getStatus() {
        return status;
    }
}
