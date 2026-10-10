package com.company.erp.masterdata.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Địa chỉ giao hàng của đối tác (BR-MD-31). Thuộc aggregate đối tác. */
@Entity
@Table(name = "partner_addresses")
public class PartnerAddress {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "address", nullable = false, length = 500)
    private String address;

    @Column(name = "receiver_name", length = 200)
    private String receiverName;

    @Column(name = "phone", length = 30)
    private String phone;

    @Column(name = "is_default", nullable = false)
    private boolean defaultAddress;

    protected PartnerAddress() {
    }

    public PartnerAddress(String address, String receiverName, String phone, boolean defaultAddress) {
        this.address = address;
        this.receiverName = receiverName;
        this.phone = phone;
        this.defaultAddress = defaultAddress;
    }

    public Long getId() {
        return id;
    }

    public String getAddress() {
        return address;
    }

    public String getReceiverName() {
        return receiverName;
    }

    public String getPhone() {
        return phone;
    }

    public boolean isDefaultAddress() {
        return defaultAddress;
    }
}
