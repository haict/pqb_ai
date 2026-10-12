package com.company.erp.finance.domain;

import com.company.erp.common.domain.AuditableEntity;
import com.company.erp.common.domain.BusinessException;
import com.company.erp.common.domain.ErrorCode;
import com.company.erp.common.domain.RecordStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;

/**
 * Quỹ tiền mặt hoặc tài khoản ngân hàng (BR-CSH-01, BR-MD-46). Số dư là bản đọc nhanh của sổ quỹ, chỉ đổi khi ghi sổ
 * chứng từ thu chi (đã khóa dòng FOR UPDATE); quỹ tiền mặt không âm (BR-CSH-03).
 */
@Entity
@Table(name = "cash_accounts")
public class CashAccount extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "company_id", nullable = false, updatable = false)
    private Long companyId;

    @Column(name = "branch_id", nullable = false, updatable = false)
    private Long branchId;

    @Column(name = "code", nullable = false, updatable = false)
    private String code;

    @Column(name = "name", nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "account_type", nullable = false, updatable = false)
    private CashAccountType accountType;

    @Column(name = "gl_account_id", nullable = false)
    private Long glAccountId;

    @Column(name = "bank_name")
    private String bankName;

    @Column(name = "bank_account_no")
    private String bankAccountNo;

    @Column(name = "currency_code", nullable = false, updatable = false)
    private String currencyCode;

    @Column(name = "balance", nullable = false, precision = 19, scale = 4)
    private BigDecimal balance;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private RecordStatus status;

    protected CashAccount() {
    }

    public CashAccount(Long companyId, Long branchId, String code, CashAccountType accountType) {
        this.companyId = companyId;
        this.branchId = branchId;
        this.code = code;
        this.accountType = accountType;
        this.currencyCode = "VND";
        this.balance = BigDecimal.ZERO;
        this.status = RecordStatus.ACTIVE;
    }

    /** Tên, tài khoản kế toán, thông tin ngân hàng; tài khoản ngân hàng bắt buộc số tài khoản. */
    public void update(String name, Long glAccountId, String bankName, String bankAccountNo) {
        if (accountType == CashAccountType.BANK && (bankAccountNo == null || bankAccountNo.isBlank())) {
            throw BusinessException.invalidField("bankAccountNo", "Tài khoản ngân hàng phải có số tài khoản.");
        }
        this.name = name;
        this.glAccountId = glAccountId;
        this.bankName = accountType == CashAccountType.BANK ? bankName : null;
        this.bankAccountNo = accountType == CashAccountType.BANK ? bankAccountNo : null;
    }

    /** Ngừng quỹ còn số dư sẽ làm mất dấu tiền thực có, nên phải chuyển hết số dư trước. */
    public void changeStatus(RecordStatus newStatus) {
        if (newStatus == RecordStatus.INACTIVE && balance.signum() != 0) {
            throw new BusinessException(ErrorCode.DATA_IN_USE, "Quỹ " + code + " còn số dư, chuyển hết số dư trước "
                    + "khi ngừng.");
        }
        this.status = newStatus;
    }

    /** Ghi tăng ({@code amount > 0}) hoặc giảm số dư; quỹ tiền mặt không được âm. */
    public void apply(BigDecimal amount) {
        BigDecimal next = balance.add(amount);
        if (accountType == CashAccountType.CASH && next.signum() < 0) {
            throw new BusinessException(ErrorCode.CASH_BALANCE_NEGATIVE, "Quỹ " + code + " không đủ tiền: còn "
                    + balance.stripTrailingZeros().toPlainString() + ", cần " + amount.negate().stripTrailingZeros()
                    .toPlainString() + ".");
        }
        this.balance = next;
    }

    public boolean isActive() {
        return status == RecordStatus.ACTIVE;
    }

    public String label() {
        return code + " - " + name;
    }

    public Long getId() {
        return id;
    }

    public Long getCompanyId() {
        return companyId;
    }

    public Long getBranchId() {
        return branchId;
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public CashAccountType getAccountType() {
        return accountType;
    }

    public Long getGlAccountId() {
        return glAccountId;
    }

    public String getBankName() {
        return bankName;
    }

    public String getBankAccountNo() {
        return bankAccountNo;
    }

    public String getCurrencyCode() {
        return currencyCode;
    }

    public BigDecimal getBalance() {
        return balance;
    }

    public RecordStatus getStatus() {
        return status;
    }
}
