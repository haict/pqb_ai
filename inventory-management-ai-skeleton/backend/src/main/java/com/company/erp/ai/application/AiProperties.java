package com.company.erp.ai.application;

import java.math.BigDecimal;
import java.net.URI;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.unit.DataSize;

/**
 * Cấu hình chức năng AI (docs/14 mục 10.1). Tắt mặc định; khi bật bắt buộc có URL và khóa gọi AI Service.
 *
 * @param enabled bật / tắt toàn bộ chức năng AI
 * @param service kết nối AI Service
 * @param ocr       giới hạn và ngưỡng OCR
 * @param chat      giới hạn hội thoại với trợ lý (AI-3)
 * @param rateLimit giới hạn tần suất theo người dùng
 */
@ConfigurationProperties(prefix = "ai")
public record AiProperties(boolean enabled, Service service, Ocr ocr, Chat chat, RateLimit rateLimit) {

    /** Khóa gọi AI Service tối thiểu 16 ký tự, trùng {@code AI_SERVICE_API_KEY} phía AI Service. */
    private static final int MIN_KEY_LENGTH = 16;

    public AiProperties {
        if (enabled && (service == null || service.baseUrl() == null || service.apiKey() == null
                || service.apiKey().length() < MIN_KEY_LENGTH)) {
            throw new IllegalArgumentException("ai.enabled=true cần ai.service.base-url và ai.service.api-key (>= "
                    + MIN_KEY_LENGTH + " ký tự)");
        }
    }

    /**
     * @param baseUrl        địa chỉ AI Service (chỉ mạng nội bộ máy chủ)
     * @param apiKey         secret header {@code X-AI-Service-Key}
     * @param connectTimeout thời gian chờ kết nối
     * @param healthInterval chu kỳ kiểm tra AI Service còn hoạt động
     */
    public record Service(URI baseUrl, String apiKey, Duration connectTimeout, Duration healthInterval) {
    }

    /**
     * @param timeout          thời gian chờ nhận dạng một chứng từ
     * @param maxFileSize      dung lượng file tối đa
     * @param maxImagePixels   số điểm ảnh tối đa của ảnh tải lên
     * @param maxConcurrent    số yêu cầu OCR chạy cùng lúc
     * @param acquireTimeout   thời gian chờ suất xử lý trước khi trả {@code AI_BUSY}
     * @param amountTolerance  chênh lệch cho phép khi kiểm tra thành tiền, tổng tiền (đồng)
     * @param maxCandidates    số ứng viên danh mục tối đa trả về
     * @param nameSimilarity   độ giống tối thiểu (0..1) để đưa tên hàng vào danh sách ứng viên
     */
    public record Ocr(Duration timeout, DataSize maxFileSize, long maxImagePixels, int maxConcurrent,
                      Duration acquireTimeout, BigDecimal amountTolerance, int maxCandidates,
                      double nameSimilarity) {
    }

    /**
     * @param timeout          thời gian chờ một câu trả lời (gồm tìm đoạn tài liệu và sinh câu trả lời)
     * @param maxConcurrent    số câu hỏi xử lý cùng lúc
     * @param acquireTimeout   thời gian chờ suất xử lý trước khi trả {@code AI_BUSY}
     * @param maxMessageChars  độ dài tối đa một câu hỏi
     * @param maxHistoryTurns  số tin nhắn gần nhất gửi kèm làm ngữ cảnh
     * @param maxContextChars  tổng ký tự tối đa của các tin nhắn gửi kèm
     * @param retentionDays    số ngày giữ hội thoại kể từ tin nhắn cuối (Q-05)
     */
    public record Chat(Duration timeout, int maxConcurrent, Duration acquireTimeout, int maxMessageChars,
                       int maxHistoryTurns, int maxContextChars, int retentionDays) {
    }

    /** @param perUserPerMinute số câu hỏi tối đa của một người dùng trong 60 giây */
    public record RateLimit(int perUserPerMinute) {
    }
}
