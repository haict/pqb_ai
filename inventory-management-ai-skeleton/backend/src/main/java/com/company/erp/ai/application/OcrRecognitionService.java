package com.company.erp.ai.application;

import com.company.erp.ai.application.OcrRequestLogService.Entry;
import com.company.erp.ai.application.dto.OcrRecognitionResponse;
import com.company.erp.ai.domain.OcrDocumentType;
import com.company.erp.common.domain.BusinessException;
import com.company.erp.common.domain.ErrorCode;
import com.company.erp.system.application.CurrentUser;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.LocalDate;
import java.util.HexFormat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

/**
 * Nhận dạng chứng từ để điền gợi ý vào form đơn mua hoặc đơn bán (D-64, docs/14 mục 4.1). Chỉ trả gợi ý: không lưu chứng từ, không
 * lưu file (BR-AI-04, BR-AI-10). Không mở transaction trong lúc chờ AI Service (docs/14 mục 9.2).
 */
@Service
public class OcrRecognitionService {

    private static final Logger log = LoggerFactory.getLogger(OcrRecognitionService.class);

    /** Phạm vi yêu cầu: công ty, chi nhánh của form đang lập; {@code documentDate} để xét hiệu lực thuế suất. */
    public record Command(Long companyId, Long branchId, OcrDocumentType documentType, LocalDate documentDate) {
    }

    private final AiAvailability availability;
    private final AiConcurrencyGuard guard;
    private final OcrFileValidator validator;
    private final AiServiceGateway gateway;
    private final PurchaseOcrMappingService purchaseMapping;
    private final SalesOcrMappingService salesMapping;
    private final OcrRequestLogService requestLog;
    private final Clock clock;

    public OcrRecognitionService(AiAvailability availability, AiConcurrencyGuard guard, OcrFileValidator validator,
                                 AiServiceGateway gateway, PurchaseOcrMappingService purchaseMapping,
                                 SalesOcrMappingService salesMapping, OcrRequestLogService requestLog, Clock clock) {
        this.availability = availability;
        this.guard = guard;
        this.validator = validator;
        this.gateway = gateway;
        this.purchaseMapping = purchaseMapping;
        this.salesMapping = salesMapping;
        this.requestLog = requestLog;
        this.clock = clock;
    }

    public OcrRecognitionResponse recognize(CurrentUser actor, Command command, MultipartFile file) {
        // docs/14 mục 6.1: ngoài ocr:use phải có quyền lập form đích của loại chứng từ.
        if (!actor.hasPermission(command.documentType().formPermission())) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        if (!actor.dataScope().canAccessCompany(command.companyId())
                || !actor.dataScope().canAccessBranch(command.branchId())) {
            throw BusinessException.notFound();
        }
        long start = clock.millis();
        String sha256 = null;
        Integer pages = null;
        String error = null;
        try {
            if (!availability.ocrAvailable()) {
                throw new BusinessException(ErrorCode.AI_SERVICE_UNAVAILABLE);
            }
            byte[] data = validator.read(file);
            sha256 = sha256(data);
            ExtractedDocument document = guard.runOcr(() -> gateway.extract(data, command.documentType()));
            pages = document.pageCount();
            LocalDate date = command.documentDate() != null ? command.documentDate() : LocalDate.now(clock);
            return command.documentType() == OcrDocumentType.SALES_ORDER
                    ? salesMapping.map(command.companyId(), date, document)
                    : purchaseMapping.map(command.companyId(), date, command.documentType(), document);
        } catch (BusinessException ex) {
            error = ex.errorCode().name();
            if (ex.errorCode() == ErrorCode.AI_SERVICE_UNAVAILABLE) {
                availability.markUnavailable();
            }
            throw ex;
        } catch (RuntimeException ex) {
            error = ErrorCode.INTERNAL_ERROR.name();
            throw ex;
        } finally {
            int duration = (int) (clock.millis() - start);
            // Chỉ metadata (BR-AI-10): không ghi tên file, nội dung nhận dạng, MST, số tiền.
            log.info("OCR {} companyId={} size={} pages={} error={} durationMs={}", command.documentType(),
                    command.companyId(), file.getSize(), pages, error == null ? "-" : error, duration);
            requestLog.record(new Entry(command.companyId(), command.branchId(), actor.id(), command.documentType(),
                    sha256, file.getSize(), pages, error, duration));
        }
    }

    private static String sha256(byte[] data) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("JVM thiếu SHA-256", ex);
        }
    }
}
