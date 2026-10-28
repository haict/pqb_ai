package com.company.erp.ai.application;

import com.company.erp.ai.domain.OcrDocumentType;
import java.util.List;

/**
 * Cổng gọi AI Service (Python, chỉ mạng nội bộ máy chủ). Lỗi chuyển sang {@code BusinessException} với mã
 * {@code OCR_*}, {@code AI_BUSY}, {@code AI_SERVICE_UNAVAILABLE}, {@code AI_TIMEOUT} (docs/14 mục 6.2).
 */
public interface AiServiceGateway {

    ExtractedDocument extract(byte[] file, OcrDocumentType documentType);

    /** Trả lời câu hỏi từ tài liệu hướng dẫn (trợ lý L1); {@code history} cũ trước, mới sau. */
    ChatReply chat(List<ChatTurn> history, String question);

    /** Trạng thái AI Service; không ném lỗi, không kết nối được thì mọi chức năng không sẵn sàng. */
    Status status();

    /** @param role USER hoặc ASSISTANT */
    record ChatTurn(String role, String content) {
    }

    /** @param grounded câu trả lời dựa trên đoạn tài liệu tìm được (false: "chưa có trong tài liệu") */
    record ChatReply(String answer, List<String> citations, boolean grounded) {

        public ChatReply {
            citations = citations == null ? List.of() : List.copyOf(citations);
        }
    }

    record Status(boolean ocrReady, boolean chatReady) {

        public static final Status DOWN = new Status(false, false);
    }
}
