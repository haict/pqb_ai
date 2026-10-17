package com.company.erp.system.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * Mốc thời điểm lớn nhất hệ thống từng thấy (D-59), một dòng {@code id = 1}. {@code checksum} do ứng dụng tính để phát
 * hiện việc sửa tay mốc này trong database.
 */
@Entity
@Table(name = "sys_license_clock")
public class LicenseClock {

    public static final byte SINGLETON_ID = 1;

    @Id
    private Byte id;

    @Column(name = "max_seen_at", nullable = false)
    private Instant maxSeenAt;

    @Column(name = "checksum", nullable = false, length = 64)
    private String checksum;

    protected LicenseClock() {
    }

    public LicenseClock(Instant maxSeenAt, String checksum) {
        this.id = SINGLETON_ID;
        this.maxSeenAt = maxSeenAt;
        this.checksum = checksum;
    }

    public void advance(Instant seenAt, String newChecksum) {
        this.maxSeenAt = seenAt;
        this.checksum = newChecksum;
    }

    public Instant getMaxSeenAt() {
        return maxSeenAt;
    }

    public String getChecksum() {
        return checksum;
    }
}
