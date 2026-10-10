package com.company.erp.masterdata.domain;

import com.company.erp.common.domain.BusinessException;
import jakarta.persistence.CascadeType;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Đối tác có một hoặc nhiều vai trò (BR-MD-30): khách hàng, NCC, đại lý, bên mời thầu... Điều khoản theo vai trò lưu
 * cùng đối tác. Liên hệ và địa chỉ giao thuộc aggregate, thay cả danh sách khi sửa.
 */
@Entity
@Table(name = "partners")
public class Partner extends CodeNameEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "legacy_code", length = 50)
    private String legacyCode;

    @Column(name = "short_name", length = 100)
    private String shortName;

    @Enumerated(EnumType.STRING)
    @Column(name = "partner_kind", nullable = false, length = 20)
    private PartnerKind partnerKind;

    @Column(name = "tax_code", length = 20)
    private String taxCode;

    @Column(name = "address", length = 500)
    private String address;

    @Column(name = "phone", length = 30)
    private String phone;

    @Column(name = "email")
    private String email;

    @Column(name = "country_code", length = 2)
    private String countryCode;

    @Column(name = "partner_group_id")
    private Long partnerGroupId;

    @Enumerated(EnumType.STRING)
    @Column(name = "customer_type", length = 30)
    private CustomerType customerType;

    @Column(name = "sales_user_id")
    private Long salesUserId;

    @Column(name = "managing_branch_id")
    private Long managingBranchId;

    @Column(name = "payment_term_days")
    private Integer paymentTermDays;

    @Column(name = "credit_limit", precision = 19, scale = 4)
    private BigDecimal creditLimit;

    @Column(name = "min_remaining_shelf_life_days")
    private Integer minRemainingShelfLifeDays;

    @Column(name = "price_list_id")
    private Long priceListId;

    @Column(name = "supplier_payment_term_days")
    private Integer supplierPaymentTermDays;

    @Column(name = "currency_code", length = 3)
    private String currencyCode;

    @Column(name = "distributed_brands", length = 500)
    private String distributedBrands;

    @Column(name = "agent_discount_percent", precision = 9, scale = 4)
    private BigDecimal agentDiscountPercent;

    @Column(name = "agent_region", length = 200)
    private String agentRegion;

    @Column(name = "agent_credit_limit", precision = 19, scale = 4)
    private BigDecimal agentCreditLimit;

    @ElementCollection
    @CollectionTable(name = "partner_roles", joinColumns = @JoinColumn(name = "partner_id"))
    @Enumerated(EnumType.STRING)
    @Column(name = "role", length = 20)
    private Set<PartnerRole> roles = EnumSet.noneOf(PartnerRole.class);

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "partner_id", nullable = false)
    @OrderBy("id")
    private List<PartnerContact> contacts = new ArrayList<>();

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "partner_id", nullable = false)
    @OrderBy("id")
    private List<PartnerAddress> addresses = new ArrayList<>();

    protected Partner() {
    }

    public Partner(Long companyId, String code, PartnerDetails details) {
        super(companyId, code, details.name());
        update(details);
    }

    public final void update(PartnerDetails details) {
        if (details.roles() == null || details.roles().isEmpty()) {
            throw BusinessException.invalidField("roles", "Đối tác phải có ít nhất một vai trò.");
        }
        rename(details.name());
        this.legacyCode = details.legacyCode();
        this.shortName = details.shortName();
        this.partnerKind = details.partnerKind();
        this.taxCode = details.taxCode();
        this.address = details.address();
        this.phone = details.phone();
        this.email = details.email();
        this.countryCode = details.countryCode();
        this.partnerGroupId = details.partnerGroupId();
        this.roles.clear();
        this.roles.addAll(details.roles());
        this.customerType = details.customerType();
        this.salesUserId = details.salesUserId();
        this.managingBranchId = details.managingBranchId();
        this.paymentTermDays = details.paymentTermDays();
        this.creditLimit = details.creditLimit();
        this.minRemainingShelfLifeDays = details.minRemainingShelfLifeDays();
        this.priceListId = details.priceListId();
        this.supplierPaymentTermDays = details.supplierPaymentTermDays();
        this.currencyCode = details.currencyCode();
        this.distributedBrands = details.distributedBrands();
        this.agentDiscountPercent = details.agentDiscountPercent();
        this.agentRegion = details.agentRegion();
        this.agentCreditLimit = details.agentCreditLimit();
    }

    public void replaceContacts(List<PartnerContact> contacts) {
        if (contacts.stream().filter(PartnerContact::isPrimaryContact).count() > 1) {
            throw BusinessException.invalidField("contacts", "Chỉ có một người liên hệ chính.");
        }
        this.contacts.clear();
        this.contacts.addAll(contacts);
    }

    public void replaceAddresses(List<PartnerAddress> addresses) {
        if (addresses.stream().filter(PartnerAddress::isDefaultAddress).count() > 1) {
            throw BusinessException.invalidField("addresses", "Chỉ có một địa chỉ giao mặc định.");
        }
        this.addresses.clear();
        this.addresses.addAll(addresses);
    }

    public boolean hasRole(PartnerRole role) {
        return roles.contains(role);
    }

    @Override
    public Long getId() {
        return id;
    }

    public String getLegacyCode() {
        return legacyCode;
    }

    public String getShortName() {
        return shortName;
    }

    public PartnerKind getPartnerKind() {
        return partnerKind;
    }

    public String getTaxCode() {
        return taxCode;
    }

    public String getAddress() {
        return address;
    }

    public String getPhone() {
        return phone;
    }

    public String getEmail() {
        return email;
    }

    public String getCountryCode() {
        return countryCode;
    }

    public Long getPartnerGroupId() {
        return partnerGroupId;
    }

    public Set<PartnerRole> getRoles() {
        return Set.copyOf(roles);
    }

    public CustomerType getCustomerType() {
        return customerType;
    }

    public Long getSalesUserId() {
        return salesUserId;
    }

    public Long getManagingBranchId() {
        return managingBranchId;
    }

    public Integer getPaymentTermDays() {
        return paymentTermDays;
    }

    public BigDecimal getCreditLimit() {
        return creditLimit;
    }

    public Integer getMinRemainingShelfLifeDays() {
        return minRemainingShelfLifeDays;
    }

    public Long getPriceListId() {
        return priceListId;
    }

    public Integer getSupplierPaymentTermDays() {
        return supplierPaymentTermDays;
    }

    public String getCurrencyCode() {
        return currencyCode;
    }

    public String getDistributedBrands() {
        return distributedBrands;
    }

    public BigDecimal getAgentDiscountPercent() {
        return agentDiscountPercent;
    }

    public String getAgentRegion() {
        return agentRegion;
    }

    public BigDecimal getAgentCreditLimit() {
        return agentCreditLimit;
    }

    public List<PartnerContact> getContacts() {
        return List.copyOf(contacts);
    }

    public List<PartnerAddress> getAddresses() {
        return List.copyOf(addresses);
    }
}
