package com.company.erp.system.application;

import com.company.erp.system.domain.AppSetting;
import com.company.erp.system.domain.PostingMode;
import com.company.erp.system.infrastructure.persistence.AppSettingRepository;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Chế độ hạch toán (D-73), đọc từ {@code sys_app_settings.posting.mode} tại thời điểm ghi sổ; sửa bằng SQL tại khách.
 * Giá trị trống hoặc sai: {@link PostingMode#FULL} (an toàn: không bỏ sót công nợ) và ghi WARN.
 */
@Service
public class PostingModeService {

    private static final Logger log = LoggerFactory.getLogger(PostingModeService.class);

    private final AppSettingRepository settings;

    public PostingModeService(AppSettingRepository settings) {
        this.settings = settings;
    }

    @Transactional(readOnly = true)
    public PostingMode mode() {
        String value = settings.findById(AppSetting.POSTING_MODE)
                .map(AppSetting::getValue)
                .map(String::trim)
                .filter(text -> !text.isEmpty())
                .orElse(null);
        if (value == null) {
            return PostingMode.FULL;
        }
        try {
            return PostingMode.valueOf(value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            log.warn("posting.mode không hợp lệ ({}), dùng FULL", value);
            return PostingMode.FULL;
        }
    }

    /** Ghi sổ đơn mua / đơn bán có ghi công nợ, bút toán, kiểm tra giá / tín dụng hay không. */
    public boolean financialPostingEnabled() {
        return mode() == PostingMode.FULL;
    }
}
