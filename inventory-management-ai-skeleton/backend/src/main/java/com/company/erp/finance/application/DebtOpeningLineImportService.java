package com.company.erp.finance.application;

import com.company.erp.common.application.excel.ExcelSupport;
import com.company.erp.common.application.excel.RowErrors;
import com.company.erp.common.domain.BusinessException;
import com.company.erp.finance.application.dto.DebtOpeningLineImportResponse;
import com.company.erp.finance.application.dto.DebtOpeningLineImportResponse.Line;
import com.company.erp.finance.domain.DebtType;
import com.company.erp.masterdata.application.MasterDataQueryService;
import com.company.erp.masterdata.application.MasterDataQueryService.PartnerInfo;
import com.company.erp.system.application.CurrentUser;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Workbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/**
 * Nhập dòng công nợ đầu kỳ từ Excel (BR-MIG-01, BR-UX-05): đọc file, tra mã đối tượng theo loại công nợ và trả danh
 * sách dòng để giao diện điền vào form. Không lưu chứng từ; người dùng kiểm tra rồi lưu như lập tay (server kiểm tra lại
 * toàn bộ khi lưu). Có lỗi ở bất kỳ dòng nào thì trả lỗi theo dòng Excel.
 */
@Service
public class DebtOpeningLineImportService {

    static final List<String> HEADERS = List.of("Mã đối tượng*", "Hạn thanh toán (dd/mm/yyyy)", "Số tiền còn nợ*",
            "Ghi chú");
    /** Bằng giới hạn số dòng của chứng từ ({@code DebtOpeningRequest.lines}). */
    static final int MAX_LINES = 1000;
    private static final int PARTNER = 0;
    private static final int DUE_DATE = 1;
    private static final int AMOUNT = 2;
    private static final int NOTE = 3;
    private static final int NOTE_MAX = 500;

    private final FinanceAccess access;
    private final MasterDataQueryService masterData;

    public DebtOpeningLineImportService(FinanceAccess access, MasterDataQueryService masterData) {
        this.access = access;
        this.masterData = masterData;
    }

    public byte[] template() {
        List<List<String>> guide = new ArrayList<>();
        guide.add(List.of("Cột", "Giá trị hợp lệ"));
        guide.add(List.of("Loại công nợ, ngày chứng từ", "Chọn trên form lập chứng từ; file chỉ chứa dòng công nợ"));
        guide.add(List.of("Mã đối tượng", "Mã đối tác đang hoạt động. Phải thu: khách hàng / bên mời thầu / đại lý / "
                + "nhân viên; phải trả: nhà cung cấp / nhân viên; tạm ứng: nhân viên. Một đối tượng được nhiều dòng"));
        guide.add(List.of("Hạn thanh toán", "Để trống: ngày chứng từ + số ngày nợ của đối tượng"));
        guide.add(List.of("Số tiền còn nợ", "Lớn hơn 0"));
        guide.add(List.of("Số dòng tối đa", String.valueOf(MAX_LINES)));
        return ExcelSupport.toBytes(ExcelSupport.template("Cong no dau ky", HEADERS, guide));
    }

    @Transactional(readOnly = true)
    public DebtOpeningLineImportResponse parse(CurrentUser actor, Long companyId, DebtType debtType,
                                               MultipartFile file) {
        if (!actor.dataScope().canAccessCompany(companyId)) {
            throw BusinessException.notFound();
        }
        access.requirePermission(actor, DebtOpeningService.CREATE);
        try (Workbook workbook = ExcelSupport.open(file)) {
            List<Row> rows = ExcelSupport.dataRows(workbook.getSheetAt(0), HEADERS.size());
            if (rows.size() > MAX_LINES) {
                throw BusinessException.invalidField("file", "Chứng từ tối đa " + MAX_LINES + " dòng; file có "
                        + rows.size() + " dòng.");
            }
            Map<String, PartnerInfo> partners = ExcelSupport.byCode(
                    masterData.partnersByCodes(companyId, ExcelSupport.codes(rows, PARTNER)), PartnerInfo::code);
            RowErrors errors = new RowErrors();
            List<Line> lines = new ArrayList<>();
            for (Row row : rows) {
                errors.row(row.getRowNum() + 1);
                Line line = line(row, debtType, partners, errors);
                if (line != null) {
                    lines.add(line);
                }
            }
            errors.throwIfAny();
            return new DebtOpeningLineImportResponse(lines.size(), lines);
        } catch (IOException ex) {
            throw BusinessException.invalidField("file", "Không đọc được file Excel (.xlsx).");
        }
    }

    private static Line line(Row row, DebtType debtType, Map<String, PartnerInfo> partners, RowErrors errors) {
        PartnerInfo partner = partner(ExcelSupport.text(row, PARTNER), debtType, partners, errors);
        LocalDate dueDate = ExcelSupport.date(row, DUE_DATE, errors, "dueDate");
        BigDecimal amount = ExcelSupport.decimal(row, AMOUNT, errors, "amount");
        if (amount == null || amount.signum() <= 0) {
            errors.add("amount", "Số tiền còn nợ bắt buộc và phải lớn hơn 0.");
        }
        String note = ExcelSupport.nullable(row, NOTE);
        if (note != null && note.length() > NOTE_MAX) {
            errors.add("note", "Ghi chú tối đa " + NOTE_MAX + " ký tự.");
        }
        if (partner == null || amount == null || amount.signum() <= 0) {
            return null;
        }
        return new Line(partner.id(), partner.code(), partner.name(), dueDate, amount, note);
    }

    private static PartnerInfo partner(String code, DebtType debtType, Map<String, PartnerInfo> partners,
                                       RowErrors errors) {
        if (code.isEmpty()) {
            errors.add("partnerId", "Mã đối tượng bắt buộc.");
            return null;
        }
        PartnerInfo partner = partners.get(code);
        if (partner == null || !partner.active()) {
            errors.add("partnerId", "Đối tượng '" + code + "' không tồn tại hoặc đã ngừng hoạt động.");
            return null;
        }
        String roleError = FinanceAccess.debtRoleError(partner, debtType);
        if (roleError != null) {
            errors.add("partnerId", roleError);
            return null;
        }
        return partner;
    }
}
