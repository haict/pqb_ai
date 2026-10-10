package com.company.erp.masterdata.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Người liên hệ của đối tác (BR-MD-31, BR-MD-34). Thuộc aggregate đối tác. */
@Entity
@Table(name = "partner_contacts")
public class PartnerContact {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "name", nullable = false, length = 200)
    private String name;

    @Column(name = "position", length = 100)
    private String position;

    @Column(name = "phone", length = 30)
    private String phone;

    @Column(name = "email")
    private String email;

    @Column(name = "is_primary", nullable = false)
    private boolean primaryContact;

    protected PartnerContact() {
    }

    public PartnerContact(String name, String position, String phone, String email, boolean primaryContact) {
        this.name = name;
        this.position = position;
        this.phone = phone;
        this.email = email;
        this.primaryContact = primaryContact;
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getPosition() {
        return position;
    }

    public String getPhone() {
        return phone;
    }

    public String getEmail() {
        return email;
    }

    public boolean isPrimaryContact() {
        return primaryContact;
    }
}
