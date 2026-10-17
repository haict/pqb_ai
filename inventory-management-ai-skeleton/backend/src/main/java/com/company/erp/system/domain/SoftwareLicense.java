package com.company.erp.system.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;

/**
 * License đã nạp (D-59). Bất biến sau khi lưu; license đang dùng là dòng mới nhất. {@code licenseText} là nguyên văn
 * license đã ký, được kiểm tra lại chữ ký mỗi lần đánh giá (không tin các cột tách sẵn nếu database bị sửa).
 */
@Entity
@Table(name = "sys_licenses")
public class SoftwareLicense {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "license_id", nullable = false, length = 100, updatable = false)
    private String licenseId;

    @Column(name = "key_id", nullable = false, length = 50, updatable = false)
    private String keyId;

    @Column(name = "customer_name", length = 300, updatable = false)
    private String customerName;

    @Column(name = "tax_codes", nullable = false, length = 1000, updatable = false)
    private String taxCodes;

    @Column(name = "valid_to", nullable = false, updatable = false)
    private LocalDate validTo;

    @Column(name = "grace_days", nullable = false, updatable = false)
    private int graceDays;

    @Column(name = "issued_at", nullable = false, updatable = false)
    private Instant issuedAt;

    @Column(name = "license_text", nullable = false, updatable = false, columnDefinition = "TEXT")
    private String licenseText;

    @Column(name = "installed_by", updatable = false)
    private Long installedBy;

    @Column(name = "installed_at", nullable = false, updatable = false)
    private Instant installedAt;

    protected SoftwareLicense() {
    }

    public SoftwareLicense(String licenseId, String keyId, String customerName, String taxCodes, LocalDate validTo,
                           int graceDays, Instant issuedAt, String licenseText, Long installedBy,
                           Instant installedAt) {
        this.licenseId = licenseId;
        this.keyId = keyId;
        this.customerName = customerName;
        this.taxCodes = taxCodes;
        this.validTo = validTo;
        this.graceDays = graceDays;
        this.issuedAt = issuedAt;
        this.licenseText = licenseText;
        this.installedBy = installedBy;
        this.installedAt = installedAt;
    }

    public Long getId() {
        return id;
    }

    public String getLicenseId() {
        return licenseId;
    }

    public String getLicenseText() {
        return licenseText;
    }

    public Instant getInstalledAt() {
        return installedAt;
    }
}
