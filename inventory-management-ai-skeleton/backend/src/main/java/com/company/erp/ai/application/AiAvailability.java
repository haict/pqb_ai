package com.company.erp.ai.application;

import com.company.erp.ai.application.AiServiceGateway.Status;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * Trạng thái AI Service kiểm tra định kỳ (docs/14 mục 9.2): AI ngừng thì trả {@code AI_SERVICE_UNAVAILABLE} ngay, không
 * để mỗi request chờ hết thời gian kết nối; giao diện ẩn nút OCR, trợ lý (BR-AI-09).
 */
@Service
public class AiAvailability {

    private static final Logger log = LoggerFactory.getLogger(AiAvailability.class);

    private final AiProperties properties;
    private final AiServiceGateway gateway;
    private final AtomicReference<Status> status = new AtomicReference<>(Status.DOWN);

    public AiAvailability(AiProperties properties, AiServiceGateway gateway) {
        this.properties = properties;
        this.gateway = gateway;
    }

    @Scheduled(fixedDelayString = "${ai.service.health-interval:PT30S}")
    public void refresh() {
        Status current = properties.enabled() ? gateway.status() : Status.DOWN;
        if (!status.getAndSet(current).equals(current) && properties.enabled()) {
            log.info("AI Service: OCR {}, trợ lý {}", label(current.ocrReady()), label(current.chatReady()));
        }
    }

    public boolean ocrAvailable() {
        return properties.enabled() && status.get().ocrReady();
    }

    public boolean chatAvailable() {
        return properties.enabled() && status.get().chatReady();
    }

    /** Gọi khi một request không kết nối được AI Service; lần kiểm tra định kỳ sau sẽ bật lại. */
    public void markUnavailable() {
        if (!status.getAndSet(Status.DOWN).equals(Status.DOWN)) {
            log.info("AI Service không khả dụng");
        }
    }

    private static String label(boolean ready) {
        return ready ? "sẵn sàng" : "không khả dụng";
    }
}
