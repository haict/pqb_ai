package com.company.erp.system.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.company.erp.common.domain.BusinessException;
import com.company.erp.system.application.dto.BrandingResponse;
import com.company.erp.system.domain.AppSetting;
import com.company.erp.system.infrastructure.persistence.AppSettingRepository;
import com.company.erp.system.infrastructure.persistence.UiFeatureRepository;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.util.unit.DataSize;

/** D-72: tên và logo theo khách hàng; giá trị sai trong DB quay về mặc định, không đọc file ngoài thư mục logo. */
class UiCustomizationServiceTest {

    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n', 0, 0, 0, 0};
    private static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 0};
    private static final byte[] WEBP = {'R', 'I', 'F', 'F', 0, 0, 0, 0, 'W', 'E', 'B', 'P', 0};

    @TempDir
    Path directory;

    private final AppSettingRepository settings = mock(AppSettingRepository.class);
    private UiCustomizationService service;

    @BeforeEach
    void setUp() {
        service = new UiCustomizationService(mock(UiFeatureRepository.class), settings,
                new BrandingProperties(directory.resolve("logos"), DataSize.ofBytes(64)));
        setting(AppSetting.APP_NAME, null);
        setting(AppSetting.LOGO_FILE, null);
    }

    @Test
    @DisplayName("D-72: chưa cấu hình thì tên, logo đều null (frontend dùng mặc định); tải logo trả 404")
    void defaultsWhenNotConfigured() {
        assertThat(service.branding()).isEqualTo(new BrandingResponse(null, null));
        assertThatThrownBy(service::requireLogo).isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("D-72: tên được cắt khoảng trắng; dài hơn 150 ký tự chỉ lấy phần đầu")
    void trimsAndLimitsAppName() {
        setting(AppSetting.APP_NAME, "  Công ty ABC  ");
        assertThat(service.branding().appName()).isEqualTo("Công ty ABC");

        setting(AppSetting.APP_NAME, "x".repeat(200));
        assertThat(service.branding().appName()).hasSize(UiCustomizationService.MAX_APP_NAME_LENGTH);
    }

    @Test
    @DisplayName("D-72: logo PNG, JPEG, WebP nhận kiểu theo chữ ký file, không theo phần mở rộng")
    void readsLogoByRealType() throws IOException {
        writeLogo("a.png", PNG);
        writeLogo("b.dat", JPEG);
        writeLogo("c.webp", WEBP);

        setting(AppSetting.LOGO_FILE, "a.png");
        assertThat(service.requireLogo().contentType()).isEqualTo("image/png");
        assertThat(service.branding().logoVersion()).isNotBlank();

        setting(AppSetting.LOGO_FILE, "b.dat");
        assertThat(service.requireLogo().contentType()).isEqualTo("image/jpeg");

        setting(AppSetting.LOGO_FILE, "c.webp");
        assertThat(service.requireLogo().contentType()).isEqualTo("image/webp");
    }

    @Test
    @DisplayName("D-72: file không phải ảnh, quá dung lượng, không tồn tại thì dùng logo mặc định")
    void ignoresInvalidLogo() throws IOException {
        writeLogo("logo.svg", "<svg onload=\"alert(1)\"/>".getBytes());
        writeLogo("big.png", concat(PNG, new byte[100]));

        for (String fileName : new String[] {"logo.svg", "big.png", "missing.png"}) {
            setting(AppSetting.LOGO_FILE, fileName);
            assertThat(service.branding().logoVersion()).as(fileName).isNull();
            assertThatThrownBy(service::requireLogo).as(fileName).isInstanceOf(BusinessException.class);
        }
    }

    @Test
    @DisplayName("D-72: tên file có đường dẫn bị từ chối, không đọc file ngoài thư mục logo")
    void rejectsPathTraversal() throws IOException {
        Files.write(directory.resolve("secret.png"), PNG);
        writeLogo("ok.png", PNG);

        for (String fileName : new String[] {"../secret.png", "..\\secret.png", "sub/ok.png", ".hidden.png",
                directory.resolve("secret.png").toString()}) {
            setting(AppSetting.LOGO_FILE, fileName);
            assertThat(service.branding().logoVersion()).as(fileName).isNull();
        }
    }

    private void setting(String key, String value) {
        AppSetting setting = mock(AppSetting.class);
        when(setting.getValue()).thenReturn(value);
        when(settings.findById(key)).thenReturn(Optional.of(setting));
    }

    private void writeLogo(String fileName, byte[] content) throws IOException {
        Files.createDirectories(directory.resolve("logos"));
        Files.write(directory.resolve("logos").resolve(fileName), content);
    }

    private static byte[] concat(byte[] first, byte[] second) {
        byte[] result = new byte[first.length + second.length];
        System.arraycopy(first, 0, result, 0, first.length);
        System.arraycopy(second, 0, result, first.length, second.length);
        return result;
    }
}
