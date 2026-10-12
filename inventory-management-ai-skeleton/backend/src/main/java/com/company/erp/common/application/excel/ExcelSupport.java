package com.company.erp.common.application.excel;

import com.company.erp.common.domain.BusinessException;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.web.multipart.MultipartFile;

/** Đọc / ghi file Excel (.xlsx) cho nhập danh mục (BR-UX-05). Kiểm tra loại thật của file (BR-SYS-22). */
public final class ExcelSupport {

    /** Số dòng dữ liệu tối đa mỗi lần nhập. */
    public static final int MAX_ROWS = 5000;
    private static final byte[] ZIP_MAGIC = {0x50, 0x4B, 0x03, 0x04};
    private static final Set<String> TRUE_VALUES = Set.of("c", "có", "co", "x", "1", "true", "yes");
    private static final Set<String> FALSE_VALUES = Set.of("", "k", "không", "khong", "0", "false", "no");
    private static final DateTimeFormatter VN_DATE = DateTimeFormatter.ofPattern("d/M/yyyy");
    private static final DataFormatter FORMATTER = new DataFormatter(Locale.ROOT);

    private ExcelSupport() {
    }

    /** Mở file .xlsx; file không phải .xlsx thật (kiểm tra chữ ký ZIP) hoặc hỏng thì báo lỗi trên trường file. */
    public static Workbook open(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw BusinessException.invalidField("file", "Chọn file Excel (.xlsx) cần nhập.");
        }
        try (InputStream input = file.getInputStream()) {
            byte[] bytes = input.readAllBytes();
            if (bytes.length < ZIP_MAGIC.length || !startsWithZipMagic(bytes)) {
                throw BusinessException.invalidField("file", "File không đúng định dạng Excel .xlsx.");
            }
            return new XSSFWorkbook(new ByteArrayInputStream(bytes));
        } catch (IOException | RuntimeException ex) {
            if (ex instanceof BusinessException business) {
                throw business;
            }
            throw BusinessException.invalidField("file", "Không đọc được file Excel (.xlsx).");
        }
    }

    public static byte[] toBytes(Workbook workbook) {
        try (workbook; ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            workbook.write(output);
            return output.toByteArray();
        } catch (IOException ex) {
            throw new IllegalStateException("Không tạo được file Excel", ex);
        }
    }

    /** Sheet mẫu: dòng tiêu đề in đậm và sheet hướng dẫn giá trị hợp lệ. */
    public static Workbook template(String sheetName, List<String> headers, List<List<String>> guide) {
        Workbook workbook = new XSSFWorkbook();
        Sheet sheet = workbook.createSheet(sheetName);
        CellStyle bold = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        bold.setFont(font);
        Row header = sheet.createRow(0);
        for (int i = 0; i < headers.size(); i++) {
            Cell cell = header.createCell(i);
            cell.setCellValue(headers.get(i));
            cell.setCellStyle(bold);
            sheet.setColumnWidth(i, Math.max(12, headers.get(i).length() + 4) * 256);
        }
        Sheet help = workbook.createSheet("Huong dan");
        for (int r = 0; r < guide.size(); r++) {
            Row row = help.createRow(r);
            for (int c = 0; c < guide.get(r).size(); c++) {
                row.createCell(c).setCellValue(guide.get(r).get(c));
            }
        }
        help.setColumnWidth(0, 30 * 256);
        help.setColumnWidth(1, 80 * 256);
        return workbook;
    }

    public static boolean isBlankRow(Row row, int columns) {
        if (row == null) {
            return true;
        }
        for (int i = 0; i < columns; i++) {
            if (!text(row, i).isEmpty()) {
                return false;
            }
        }
        return true;
    }

    /** Giá trị ô dạng chuỗi đã bỏ khoảng trắng hai đầu; ô trống trả chuỗi rỗng. */
    public static String text(Row row, int column) {
        Cell cell = row == null ? null : row.getCell(column);
        if (cell == null) {
            return "";
        }
        if (cell.getCellType() == CellType.NUMERIC && !DateUtil.isCellDateFormatted(cell)) {
            BigDecimal value = BigDecimal.valueOf(cell.getNumericCellValue()).stripTrailingZeros();
            return value.toPlainString();
        }
        return FORMATTER.formatCellValue(cell).trim();
    }

    public static String nullable(Row row, int column) {
        String value = text(row, column);
        return value.isEmpty() ? null : value;
    }

    public static boolean flag(Row row, int column, RowErrors errors, String field) {
        String value = text(row, column).toLowerCase(Locale.ROOT);
        if (TRUE_VALUES.contains(value)) {
            return true;
        }
        if (!FALSE_VALUES.contains(value)) {
            errors.add(field, "Giá trị Có/Không không hợp lệ: " + value);
        }
        return false;
    }

    public static Integer integer(Row row, int column, RowErrors errors, String field) {
        BigDecimal value = decimal(row, column, errors, field);
        if (value == null) {
            return null;
        }
        try {
            return value.intValueExact();
        } catch (ArithmeticException ex) {
            errors.add(field, "Phải là số nguyên.");
            return null;
        }
    }

    public static BigDecimal decimal(Row row, int column, RowErrors errors, String field) {
        String value = text(row, column);
        if (value.isEmpty()) {
            return null;
        }
        try {
            return new BigDecimal(value.replace(",", ""));
        } catch (NumberFormatException ex) {
            errors.add(field, "Phải là số: " + value);
            return null;
        }
    }

    public static LocalDate date(Row row, int column, RowErrors errors, String field) {
        Cell cell = row.getCell(column);
        if (cell == null || text(row, column).isEmpty()) {
            return null;
        }
        if (cell.getCellType() == CellType.NUMERIC && DateUtil.isCellDateFormatted(cell)) {
            return cell.getLocalDateTimeCellValue().toLocalDate();
        }
        try {
            return LocalDate.parse(text(row, column), VN_DATE);
        } catch (DateTimeParseException ex) {
            errors.add(field, "Ngày phải có dạng ngày/tháng/năm: " + text(row, column));
            return null;
        }
    }

    /** Số dòng dữ liệu không vượt {@link #MAX_ROWS}. */
    public static void requireRowCount(Sheet sheet) {
        if (sheet.getLastRowNum() > MAX_ROWS) {
            throw BusinessException.invalidField("file", "File vượt " + MAX_ROWS + " dòng dữ liệu.");
        }
    }

    public static void requireNotEmpty(boolean empty) {
        if (empty) {
            throw BusinessException.invalidField("file", "File không có dòng dữ liệu nào.");
        }
    }

    /** Dấu vân tay nội dung file, dùng làm nội dung so khớp của Idempotency-Key khi nhập. */
    public static String sha256(MultipartFile file) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(file.getBytes()));
        } catch (IOException | NoSuchAlgorithmException ex) {
            throw BusinessException.invalidField("file", "Không đọc được file.");
        }
    }

    private static boolean startsWithZipMagic(byte[] bytes) {
        for (int i = 0; i < ZIP_MAGIC.length; i++) {
            if (bytes[i] != ZIP_MAGIC[i]) {
                return false;
            }
        }
        return true;
    }
}
