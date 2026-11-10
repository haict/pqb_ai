package com.company.erp.system.application;

/** Nhận kiểu ảnh logo theo chữ ký đầu file (D-72), không theo phần mở rộng; chỉ PNG, JPEG, WebP (không SVG). */
final class LogoImageType {

    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'};
    private static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};
    private static final byte[] RIFF = {'R', 'I', 'F', 'F'};
    private static final byte[] WEBP = {'W', 'E', 'B', 'P'};
    private static final int WEBP_MARKER_OFFSET = 8;

    private LogoImageType() {
    }

    /** Kiểu MIME của ảnh, hoặc {@code null} khi không phải PNG / JPEG / WebP. */
    static String detect(byte[] content) {
        if (startsWith(content, PNG, 0)) {
            return "image/png";
        }
        if (startsWith(content, JPEG, 0)) {
            return "image/jpeg";
        }
        if (startsWith(content, RIFF, 0) && startsWith(content, WEBP, WEBP_MARKER_OFFSET)) {
            return "image/webp";
        }
        return null;
    }

    private static boolean startsWith(byte[] content, byte[] signature, int offset) {
        if (content.length < offset + signature.length) {
            return false;
        }
        for (int i = 0; i < signature.length; i++) {
            if (content[offset + i] != signature[i]) {
                return false;
            }
        }
        return true;
    }
}
