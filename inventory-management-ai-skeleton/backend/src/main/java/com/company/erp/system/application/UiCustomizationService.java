package com.company.erp.system.application;

import com.company.erp.common.domain.BusinessException;
import com.company.erp.system.application.dto.BrandingResponse;
import com.company.erp.system.application.dto.UiFeatureResponse;
import com.company.erp.system.domain.AppSetting;
import com.company.erp.system.domain.UiFeature;
import com.company.erp.system.infrastructure.persistence.AppSettingRepository;
import com.company.erp.system.infrastructure.persistence.UiFeatureRepository;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Tùy biến giao diện theo khách hàng (D-72): ẩn / đổi tên menu, nút; tên và logo. Dữ liệu sửa bằng SQL tại khách nên
 * giá trị sai (tên file lạ, file không phải ảnh, quá lớn) chỉ ghi WARN và quay về mặc định, không làm hỏng trang.
 */
@Service
public class UiCustomizationService {

    private static final Logger log = LoggerFactory.getLogger(UiCustomizationService.class);

    static final int MAX_APP_NAME_LENGTH = 150;
    /** Chỉ tên file, không đường dẫn: chặn đọc file ngoài thư mục logo. */
    private static final Pattern SAFE_FILE_NAME = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._-]{0,99}");

    private final UiFeatureRepository featureRepository;
    private final AppSettingRepository settingRepository;
    private final BrandingProperties properties;

    public UiCustomizationService(UiFeatureRepository featureRepository, AppSettingRepository settingRepository,
                                  BrandingProperties properties) {
        this.featureRepository = featureRepository;
        this.settingRepository = settingRepository;
        this.properties = properties;
    }

    /** Toàn bộ cấu hình menu / nút (bảng nhỏ, không phân trang). Khóa không có dòng: frontend coi là bật. */
    @Transactional(readOnly = true)
    public List<UiFeatureResponse> features() {
        return featureRepository.findAll().stream()
                .map(UiCustomizationService::toResponse)
                .toList();
    }

    public BrandingResponse branding() {
        return new BrandingResponse(appName(), logo().map(BrandingLogo::version).orElse(null));
    }

    /** Logo đang cấu hình; chưa cấu hình hoặc không hợp lệ: 404, frontend dùng logo mặc định. */
    public BrandingLogo requireLogo() {
        return logo().orElseThrow(BusinessException::notFound);
    }

    private String appName() {
        String name = setting(AppSetting.APP_NAME);
        if (name != null && name.length() > MAX_APP_NAME_LENGTH) {
            log.warn("branding.app-name dài hơn {} ký tự, chỉ hiển thị phần đầu", MAX_APP_NAME_LENGTH);
            return name.substring(0, MAX_APP_NAME_LENGTH);
        }
        return name;
    }

    private Optional<BrandingLogo> logo() {
        String fileName = setting(AppSetting.LOGO_FILE);
        if (fileName == null) {
            return Optional.empty();
        }
        if (!SAFE_FILE_NAME.matcher(fileName).matches()) {
            log.warn("branding.logo-file không hợp lệ (chỉ tên file, không đường dẫn): dùng logo mặc định");
            return Optional.empty();
        }
        Path directory = properties.directory().toAbsolutePath().normalize();
        Path file = directory.resolve(fileName).normalize();
        if (!file.startsWith(directory) || !Files.isRegularFile(file)) {
            log.warn("Không thấy file logo {} trong {}: dùng logo mặc định", fileName, directory);
            return Optional.empty();
        }
        return readLogo(file);
    }

    private Optional<BrandingLogo> readLogo(Path file) {
        try {
            if (Files.size(file) > properties.logoMaxSize().toBytes()) {
                log.warn("File logo {} lớn hơn {}: dùng logo mặc định", file.getFileName(), properties.logoMaxSize());
                return Optional.empty();
            }
            byte[] content = Files.readAllBytes(file);
            String contentType = LogoImageType.detect(content);
            if (contentType == null) {
                log.warn("File logo {} không phải PNG, JPEG, WebP: dùng logo mặc định", file.getFileName());
                return Optional.empty();
            }
            String version = Long.toString(Files.getLastModifiedTime(file).toMillis(), Character.MAX_RADIX);
            return Optional.of(new BrandingLogo(content, contentType, version));
        } catch (IOException e) {
            log.warn("Không đọc được file logo {}: dùng logo mặc định", file.getFileName(), e);
            return Optional.empty();
        }
    }

    private String setting(String key) {
        return settingRepository.findById(key)
                .map(AppSetting::getValue)
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .orElse(null);
    }

    private static UiFeatureResponse toResponse(UiFeature feature) {
        String label = feature.getCustomLabel();
        String trimmed = label == null || label.isBlank() ? null : label.trim();
        return new UiFeatureResponse(feature.getKey(), feature.isEnabled(), trimmed);
    }
}
