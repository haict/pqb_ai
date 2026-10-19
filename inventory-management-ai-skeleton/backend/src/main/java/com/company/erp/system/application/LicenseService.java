package com.company.erp.system.application;

import com.company.erp.common.application.AuditEntry;
import com.company.erp.common.application.AuditLogService;
import com.company.erp.common.application.BusinessCalendar;
import com.company.erp.common.domain.BusinessException;
import com.company.erp.common.domain.ErrorCode;
import com.company.erp.common.domain.RecordStatus;
import com.company.erp.system.application.dto.LicenseStatusResponse;
import com.company.erp.system.domain.Company;
import com.company.erp.system.domain.LicenseClock;
import com.company.erp.system.domain.LicenseState;
import com.company.erp.system.domain.SoftwareLicense;
import com.company.erp.system.infrastructure.persistence.CompanyRepository;
import com.company.erp.system.infrastructure.persistence.LicenseClockRepository;
import com.company.erp.system.infrastructure.persistence.SoftwareLicenseRepository;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Bản quyền phần mềm (D-59): đánh giá license khi khởi động, định kỳ và khi nạp; giữ kết quả trong bộ nhớ để bộ lọc
 * ghi dữ liệu kiểm tra nhanh ở mỗi request. Không ghi được (chỉ đọc) khi: chưa có license, chữ ký sai, đồng hồ máy chủ
 * bị lùi, có công ty đang hoạt động ngoài danh sách mã số thuế, hoặc quá thời gian ân hạn.
 */
@Service
public class LicenseService {

    static final String AUDIT_ENTITY = "SOFTWARE_LICENSE";
    private static final Logger log = LoggerFactory.getLogger(LicenseService.class);
    /** Sai lệch đồng hồ chấp nhận được (đổi giờ, đồng bộ NTP) trước khi coi là lùi đồng hồ. */
    private static final Duration CLOCK_TOLERANCE = Duration.ofDays(1);
    private static final DateTimeFormatter VN_DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    /** Khóa mã kiểm tra mốc thời gian: chống sửa tay trong database, không phải bí mật mật mã học. */
    private static final byte[] CLOCK_CHECKSUM_KEY =
            "erp-license-clock-v1:5b0e2c7a-91d4-4f6b-8a3e-d27c1f0e6a94".getBytes(StandardCharsets.UTF_8);

    private final SoftwareLicenseRepository licenseRepository;
    private final LicenseClockRepository clockRepository;
    private final CompanyRepository companyRepository;
    private final LicenseVerifier verifier;
    private final AuditLogService auditLog;
    private final BusinessCalendar calendar;
    private final Clock clock;
    private final LicenseProperties properties;
    private final ResourceLoader resourceLoader;
    private volatile Evaluation current = Evaluation.blocked(LicenseState.MISSING, null, null, List.of());

    public LicenseService(SoftwareLicenseRepository licenseRepository, LicenseClockRepository clockRepository,
                          CompanyRepository companyRepository, LicenseVerifier verifier, AuditLogService auditLog,
                          BusinessCalendar calendar, Clock clock, LicenseProperties properties,
                          ResourceLoader resourceLoader) {
        this.licenseRepository = licenseRepository;
        this.clockRepository = clockRepository;
        this.companyRepository = companyRepository;
        this.verifier = verifier;
        this.auditLog = auditLog;
        this.calendar = calendar;
        this.clock = clock;
        this.properties = properties;
        this.resourceLoader = resourceLoader;
    }

    /**
     * Kết quả đánh giá. {@code blockedState} khác null: chỉ đọc bất kể ngày; null: trạng thái theo ngày hiện tại so với
     * ngày hết hạn / ân hạn (để hết hạn có hiệu lực ngay khi qua ngày, không chờ lần đánh giá lại).
     */
    private record Evaluation(LicenseState blockedState, LicensePayload payload, SoftwareLicense record,
                              List<String> unlicensedCompanies) {

        static Evaluation blocked(LicenseState state, LicensePayload payload, SoftwareLicense record,
                                  List<String> unlicensedCompanies) {
            return new Evaluation(state, payload, record, unlicensedCompanies);
        }

        LicenseState state(LocalDate today) {
            if (blockedState != null) {
                return blockedState;
            }
            if (!today.isAfter(payload.validTo())) {
                return LicenseState.VALID;
            }
            return today.isAfter(payload.graceUntil()) ? LicenseState.EXPIRED : LicenseState.GRACE;
        }
    }

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void onStartup() {
        importConfiguredLicense();
        refresh();
    }

    @Scheduled(fixedDelayString = "${erp.license.refresh-interval:PT1H}",
            initialDelayString = "${erp.license.refresh-interval:PT1H}")
    @Transactional
    public void refresh() {
        current = evaluate();
        LicenseState state = current.state(calendar.today());
        if (!state.writable()) {
            log.warn("Bản quyền phần mềm: {} - hệ thống ở chế độ chỉ đọc.", state);
        }
    }

    /** Có được ghi dữ liệu không; gọi ở mỗi request ghi nên chỉ đọc kết quả trong bộ nhớ. */
    public boolean writable() {
        return current.state(calendar.today()).writable();
    }

    public LicenseStatusResponse status() {
        return toResponse(current, calendar.today());
    }

    @Transactional
    public LicenseStatusResponse install(CurrentUser actor, String licenseText) {
        if (!actor.hasPermission("license:manage")) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        String text = licenseText.trim();
        LicensePayload payload = verifier.verify(text);
        if (payload.graceUntil().isBefore(calendar.today())) {
            throw BusinessException.invalidField("license", "License đã hết hạn (hết ân hạn ngày "
                    + payload.graceUntil() + "). Liên hệ nhà cung cấp để được cấp license mới.");
        }
        if (payload.issuedAt().isAfter(clock.instant().plus(CLOCK_TOLERANCE))) {
            throw BusinessException.invalidField("license", "Ngày cấp license sau thời điểm hiện tại của máy chủ; "
                    + "kiểm tra lại đồng hồ máy chủ.");
        }
        SoftwareLicense saved = save(payload, text, actor.id());
        audit(actor.id(), "INSTALL", saved, payload);
        refresh();
        return status();
    }

    private void importConfiguredLicense() {
        String location = properties.importLocation();
        if (location == null || location.isBlank()) {
            return;
        }
        Resource resource = resourceLoader.getResource(location.trim());
        if (!resource.exists()) {
            log.warn("Không tìm thấy file license cấu hình tại {}", location);
            return;
        }
        try (InputStream input = resource.getInputStream()) {
            String text = new String(input.readAllBytes(), StandardCharsets.UTF_8).trim();
            LicensePayload payload = verifier.verify(text);
            Optional<LicensePayload> installed = installedPayload();
            // Chỉ nạp file mới hơn: file cũ còn trong cấu hình không đè license vừa nạp qua màn hình.
            if (installed.isPresent() && !payload.issuedAt().isAfter(installed.get().issuedAt())) {
                return;
            }
            SoftwareLicense saved = save(payload, text, null);
            audit(null, "IMPORT", saved, payload);
            log.info("Đã nạp license {} từ file cấu hình", payload.licenseId());
        } catch (IOException ex) {
            log.warn("Không đọc được file license tại {}", location);
        } catch (BusinessException ex) {
            log.warn("File license tại {} không hợp lệ: {}", location, ex.getMessage());
        }
    }

    private Optional<LicensePayload> installedPayload() {
        return licenseRepository.findTopByOrderByIdDesc().flatMap(record -> {
            try {
                return Optional.of(verifier.verify(record.getLicenseText()));
            } catch (BusinessException ex) {
                return Optional.empty();
            }
        });
    }

    private Evaluation evaluate() {
        Optional<SoftwareLicense> latest = licenseRepository.findTopByOrderByIdDesc();
        if (latest.isEmpty()) {
            return Evaluation.blocked(LicenseState.MISSING, null, null, List.of());
        }
        SoftwareLicense record = latest.get();
        LicensePayload payload;
        try {
            payload = verifier.verify(record.getLicenseText());
        } catch (BusinessException ex) {
            return Evaluation.blocked(LicenseState.INVALID, null, record, List.of());
        }
        if (!clockConsistent(payload, record)) {
            return Evaluation.blocked(LicenseState.CLOCK_TAMPERED, payload, record, List.of());
        }
        List<String> unlicensed = companyRepository.findByStatus(RecordStatus.ACTIVE).stream()
                .filter(company -> !payload.covers(company.getTaxCode()))
                .map(LicenseService::companyLabel)
                .toList();
        if (!unlicensed.isEmpty()) {
            return Evaluation.blocked(LicenseState.COMPANY_NOT_LICENSED, payload, record, unlicensed);
        }
        return new Evaluation(null, payload, record, List.of());
    }

    /**
     * Đồng hồ máy chủ không được lùi quá {@link #CLOCK_TOLERANCE} so với: ngày cấp license, lần nạp license, mốc thời
     * điểm lớn nhất đã thấy. Mốc bị sửa tay (sai mã kiểm tra) cũng coi là lùi đồng hồ.
     */
    private boolean clockConsistent(LicensePayload payload, SoftwareLicense record) {
        Instant now = clock.instant().truncatedTo(ChronoUnit.MILLIS);
        Instant latestPlausible = now.plus(CLOCK_TOLERANCE);
        if (payload.issuedAt().isAfter(latestPlausible) || record.getInstalledAt().isAfter(latestPlausible)) {
            return false;
        }
        Optional<LicenseClock> stored = clockRepository.findById(LicenseClock.SINGLETON_ID);
        if (stored.isEmpty()) {
            clockRepository.save(new LicenseClock(now, checksum(now)));
            return true;
        }
        LicenseClock mark = stored.get();
        if (!checksum(mark.getMaxSeenAt()).equals(mark.getChecksum()) || mark.getMaxSeenAt().isAfter(latestPlausible)) {
            return false;
        }
        if (now.isAfter(mark.getMaxSeenAt())) {
            mark.advance(now, checksum(now));
        }
        return true;
    }

    private static String checksum(Instant instant) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(CLOCK_CHECKSUM_KEY, "HmacSHA256"));
            byte[] digest = mac.doFinal(Long.toString(instant.toEpochMilli()).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("Không tính được mã kiểm tra mốc thời gian", ex);
        }
    }

    private SoftwareLicense save(LicensePayload payload, String text, Long actorId) {
        return licenseRepository.saveAndFlush(new SoftwareLicense(payload.licenseId(), payload.keyId(),
                payload.customerName(), String.join(",", payload.taxCodes()), payload.validTo(), payload.graceDays(),
                payload.issuedAt(), text, actorId, clock.instant().truncatedTo(ChronoUnit.MILLIS)));
    }

    private void audit(Long actorId, String action, SoftwareLicense saved, LicensePayload payload) {
        auditLog.record(new AuditEntry(actorId, action, AUDIT_ENTITY, saved.getId(), payload.licenseId(), null, null,
                Map.of("customerName", String.valueOf(payload.customerName()), "taxCodes", payload.taxCodes(),
                        "validTo", payload.validTo().toString(), "graceDays", payload.graceDays()),
                null, null));
    }

    private static String companyLabel(Company company) {
        String taxCode = company.getTaxCode() == null || company.getTaxCode().isBlank()
                ? "chưa có mã số thuế" : "MST " + company.getTaxCode();
        return company.getCode() + " - " + company.getName() + " (" + taxCode + ")";
    }

    private static LicenseStatusResponse toResponse(Evaluation evaluation, LocalDate today) {
        LicenseState state = evaluation.state(today);
        LicensePayload payload = evaluation.payload();
        SoftwareLicense record = evaluation.record();
        return new LicenseStatusResponse(state, state.writable(), message(state, payload, today),
                payload == null ? null : payload.licenseId(),
                payload == null ? null : payload.customerName(),
                payload == null ? List.of() : payload.taxCodes(),
                payload == null ? null : payload.validTo(),
                payload == null ? null : payload.graceUntil(),
                payload == null ? null : ChronoUnit.DAYS.between(today, payload.graceUntil()),
                payload == null ? null : payload.issuedAt(),
                record == null ? null : record.getInstalledAt(),
                evaluation.unlicensedCompanies());
    }

    /** Số ngày đến hết hạn và đến hết ân hạn tách riêng, để không hiểu nhầm ngày ân hạn là ngày còn hiệu lực. */
    private static String validMessage(LicensePayload payload, LocalDate today) {
        long daysToExpiry = ChronoUnit.DAYS.between(today, payload.validTo());
        String expiry = daysToExpiry == 0 ? "hết hạn hôm nay (" + VN_DATE.format(payload.validTo()) + ")"
                : "còn hiệu lực đến ngày " + VN_DATE.format(payload.validTo()) + " (còn " + daysToExpiry + " ngày)";
        String grace = payload.graceDays() == 0 ? ""
                : "; sau đó có " + payload.graceDays() + " ngày ân hạn đến hết ngày "
                        + VN_DATE.format(payload.graceUntil());
        return "Bản quyền " + expiry + grace + ".";
    }

    private static String message(LicenseState state, LicensePayload payload, LocalDate today) {
        return switch (state) {
            case VALID -> validMessage(payload, today);
            case GRACE -> "Bản quyền đã hết hạn ngày " + VN_DATE.format(payload.validTo()) + "; còn "
                    + ChronoUnit.DAYS.between(today, payload.graceUntil()) + " ngày ân hạn (đến hết ngày "
                    + VN_DATE.format(payload.graceUntil()) + ") trước khi hệ thống chuyển sang chế độ chỉ đọc.";
            case EXPIRED -> "Bản quyền đã hết hạn và hết ân hạn: hệ thống chỉ cho xem, in, xuất dữ liệu.";
            case MISSING -> "Chưa nạp bản quyền phần mềm: hệ thống chỉ cho xem, in, xuất dữ liệu.";
            case INVALID -> "Bản quyền không hợp lệ: hệ thống chỉ cho xem, in, xuất dữ liệu.";
            case CLOCK_TAMPERED -> "Đồng hồ máy chủ không hợp lệ (bị lùi): hệ thống chỉ cho xem, in, xuất dữ liệu. "
                    + "Chỉnh lại giờ máy chủ hoặc liên hệ nhà cung cấp.";
            case COMPANY_NOT_LICENSED -> "Có công ty đang hoạt động không thuộc bản quyền: hệ thống chỉ cho xem, in, "
                    + "xuất dữ liệu. Ngừng hoạt động công ty đó hoặc xin license bổ sung.";
        };
    }
}
