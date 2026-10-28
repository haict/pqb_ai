package com.company.erp.ai.application;

import com.company.erp.common.domain.BusinessException;
import com.company.erp.common.domain.ErrorCode;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Iterator;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

/**
 * Kiểm tra file trước khi gửi AI Service (docs/14 mục 4.2, BR-SEC-04): dung lượng, loại thật theo chữ ký byte đầu file
 * (không tin phần mở rộng, Content-Type), số điểm ảnh đọc từ header ảnh trước khi giải mã. Số trang PDF do AI Service
 * kiểm tra khi render (không thêm thư viện PDF vào backend).
 */
@Component
public class OcrFileValidator {

    enum FileKind { JPEG, PNG, PDF }

    private final AiProperties properties;

    public OcrFileValidator(AiProperties properties) {
        this.properties = properties;
    }

    /** Trả nội dung file đã kiểm tra; file chỉ nằm trong bộ nhớ, không ghi ra đĩa (BR-AI-10). */
    public byte[] read(MultipartFile file) {
        if (file.getSize() > properties.ocr().maxFileSize().toBytes()) {
            throw new BusinessException(ErrorCode.OCR_FILE_TOO_LARGE);
        }
        byte[] data;
        try {
            data = file.getBytes();
        } catch (IOException ex) {
            throw new BusinessException(ErrorCode.OCR_FILE_INVALID);
        }
        if (data.length == 0) {
            throw new BusinessException(ErrorCode.OCR_FILE_INVALID);
        }
        FileKind kind = kind(data);
        if (kind != FileKind.PDF) {
            checkPixels(data);
        }
        return data;
    }

    static FileKind kind(byte[] data) {
        if (startsWith(data, 0xFF, 0xD8, 0xFF)) {
            return FileKind.JPEG;
        }
        if (startsWith(data, 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A)) {
            return FileKind.PNG;
        }
        if (startsWith(data, '%', 'P', 'D', 'F', '-')) {
            return FileKind.PDF;
        }
        throw new BusinessException(ErrorCode.OCR_UNSUPPORTED_TYPE);
    }

    private void checkPixels(byte[] data) {
        try (ImageInputStream input = ImageIO.createImageInputStream(new ByteArrayInputStream(data))) {
            Iterator<ImageReader> readers = input == null ? null : ImageIO.getImageReaders(input);
            if (readers == null || !readers.hasNext()) {
                throw new BusinessException(ErrorCode.OCR_FILE_INVALID);
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(input, true, true);
                long pixels = (long) reader.getWidth(0) * reader.getHeight(0);
                if (pixels <= 0 || pixels > properties.ocr().maxImagePixels()) {
                    throw new BusinessException(ErrorCode.OCR_FILE_INVALID, "Ảnh vượt kích thước cho phép.");
                }
            } finally {
                reader.dispose();
            }
        } catch (IOException ex) {
            throw new BusinessException(ErrorCode.OCR_FILE_INVALID);
        }
    }

    private static boolean startsWith(byte[] data, int... signature) {
        if (data.length < signature.length) {
            return false;
        }
        for (int i = 0; i < signature.length; i++) {
            if ((data[i] & 0xFF) != signature[i]) {
                return false;
            }
        }
        return true;
    }
}
