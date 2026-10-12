package com.company.erp.accounting.application;

import com.company.erp.accounting.application.dto.AccountImportResponse;
import com.company.erp.accounting.domain.Account;
import com.company.erp.accounting.domain.BalanceSide;
import com.company.erp.accounting.infrastructure.persistence.AccountRepository;
import com.company.erp.common.application.IdempotencyService;
import com.company.erp.common.application.IdempotentCommand;
import com.company.erp.common.application.excel.ExcelSupport;
import com.company.erp.common.application.excel.RowErrors;
import com.company.erp.common.domain.BusinessException;
import com.company.erp.system.application.CurrentUser;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/**
 * Nhập hệ thống tài khoản từ Excel (BR-ACC-01, BR-MIG-01): cách nạp bộ tài khoản theo chế độ kế toán áp dụng ở
 * production, sau khi kế toán trưởng xác nhận. Tất cả hoặc không; lỗi trả theo dòng. Tài khoản cha có thể nằm trong
 * cùng file hoặc đã có trong hệ thống.
 */
@Service
public class AccountImportService {

    private static final List<String> HEADERS = List.of("Mã TK", "Tên TK", "Mã TK cha", "Tính chất",
            "Theo dõi đối tượng");
    private static final Pattern CODE = Pattern.compile("[0-9A-Z_.-]{1,20}");
    private static final Map<String, BalanceSide> SIDES = Map.of("NO", BalanceSide.DEBIT, "NỢ", BalanceSide.DEBIT,
            "CO", BalanceSide.CREDIT, "CÓ", BalanceSide.CREDIT, "LT", BalanceSide.BOTH, "LƯỠNG TÍNH", BalanceSide.BOTH);

    private final AccountRepository repository;
    private final AccountService accountService;
    private final AccountingAccess access;
    private final IdempotencyService idempotency;

    public AccountImportService(AccountRepository repository, AccountService accountService, AccountingAccess access,
                                IdempotencyService idempotency) {
        this.repository = repository;
        this.accountService = accountService;
        this.access = access;
        this.idempotency = idempotency;
    }

    public byte[] template() {
        List<List<String>> guide = new ArrayList<>();
        guide.add(List.of("Cột", "Giá trị hợp lệ"));
        guide.add(List.of("Mã TK", "1-20 ký tự 0-9, A-Z, _ . -; mã tài khoản con bắt đầu bằng mã tài khoản cha"));
        guide.add(List.of("Mã TK cha", "Để trống với tài khoản cấp 1; tài khoản cha nằm trong file hoặc đã có"));
        guide.add(List.of("Tính chất", "NO (dư Nợ), CO (dư Có), LT (lưỡng tính)"));
        guide.add(List.of("Theo dõi đối tượng", "C (bắt buộc chi tiết theo khách hàng / nhà cung cấp / nhân viên), "
                + "K hoặc để trống"));
        guide.add(List.of("Số dòng tối đa", String.valueOf(ExcelSupport.MAX_ROWS)));
        return ExcelSupport.toBytes(ExcelSupport.template("He thong tai khoan", HEADERS, guide));
    }

    @Transactional
    public AccountImportResponse importAccounts(CurrentUser actor, Long companyId, String idempotencyKey,
                                                MultipartFile file) {
        access.requirePermission(actor, AccountService.MANAGE);
        access.requireCompany(actor, companyId);
        IdempotentCommand command = new IdempotentCommand(actor.id(), idempotencyKey, "POST /api/v1/accounts/import",
                Map.of("companyId", companyId, "sha256", ExcelSupport.sha256(file)));
        return idempotency.execute(command, AccountImportResponse.class, () -> {
            List<ParsedAccount> rows = parse(companyId, file);
            Map<String, Account> created = new HashMap<>();
            for (ParsedAccount row : rows) {
                Account parent = row.parentCode() == null ? null : created.get(row.parentCode());
                if (row.parentCode() != null && parent == null) {
                    Account existing = repository.findByCompanyIdAndCode(companyId, row.parentCode()).orElseThrow();
                    parent = accountService.lockParent(companyId, existing.getId());
                }
                created.put(row.code(), accountService.createAccount(companyId, row.code(), row.name(), parent,
                        row.side(), row.partnerRequired()));
            }
            AccountImportResponse result = new AccountImportResponse(rows.size());
            access.audit(actor, "IMPORT", AccountingAccess.ENTITY_ACCOUNT, null, file.getOriginalFilename(), null,
                    result, companyId);
            return result;
        });
    }

    private record ParsedAccount(int rowNumber, String code, String name, String parentCode, BalanceSide side,
                                 boolean partnerRequired) {
    }

    /** Đọc và kiểm tra toàn bộ file; sắp theo mã để tài khoản cha luôn được tạo trước tài khoản con. */
    private List<ParsedAccount> parse(Long companyId, MultipartFile file) {
        RowErrors errors = new RowErrors();
        Map<String, ParsedAccount> rows = new HashMap<>();
        try (Workbook workbook = ExcelSupport.open(file)) {
            Sheet sheet = workbook.getSheetAt(0);
            ExcelSupport.requireRowCount(sheet);
            for (int index = 1; index <= sheet.getLastRowNum(); index++) {
                Row row = sheet.getRow(index);
                if (ExcelSupport.isBlankRow(row, HEADERS.size())) {
                    continue;
                }
                errors.row(index + 1);
                ParsedAccount parsed = parseRow(companyId, index + 1, row, errors, rows);
                if (parsed != null) {
                    rows.put(parsed.code(), parsed);
                }
            }
        } catch (IOException ex) {
            throw BusinessException.invalidField("file", "Không đọc được file Excel (.xlsx).");
        }
        ExcelSupport.requireNotEmpty(rows.isEmpty());
        for (ParsedAccount row : rows.values()) {
            errors.row(row.rowNumber());
            checkParent(companyId, row, rows, errors);
        }
        errors.throwIfAny();
        return rows.values().stream().sorted(Comparator.comparing(ParsedAccount::code)).toList();
    }

    private ParsedAccount parseRow(Long companyId, int rowNumber, Row row, RowErrors errors,
                                   Map<String, ParsedAccount> rows) {
        String code = ExcelSupport.text(row, 0).toUpperCase(Locale.ROOT);
        boolean valid = true;
        if (!CODE.matcher(code).matches()) {
            errors.add("code", "Mã TK bắt buộc, 1-20 ký tự 0-9, A-Z, _ . -");
            valid = false;
        } else if (rows.containsKey(code)) {
            errors.add("code", "Mã TK '" + code + "' trùng trong file.");
            valid = false;
        } else if (repository.existsByCompanyIdAndCode(companyId, code)) {
            errors.add("code", "Mã TK '" + code + "' đã tồn tại.");
            valid = false;
        }
        String name = ExcelSupport.text(row, 1);
        if (name.isEmpty() || name.length() > 255) {
            errors.add("name", "Tên TK bắt buộc, tối đa 255 ký tự.");
            valid = false;
        }
        String parentCode = ExcelSupport.nullable(row, 2);
        BalanceSide side = SIDES.get(ExcelSupport.text(row, 3).toUpperCase(Locale.ROOT));
        if (side == null) {
            errors.add("balanceSide", "Tính chất phải là NO, CO hoặc LT.");
            valid = false;
        }
        boolean partnerRequired = ExcelSupport.flag(row, 4, errors, "partnerRequired");
        return valid ? new ParsedAccount(rowNumber, code, name,
                parentCode == null ? null : parentCode.toUpperCase(Locale.ROOT), side, partnerRequired) : null;
    }

    private void checkParent(Long companyId, ParsedAccount row, Map<String, ParsedAccount> rows, RowErrors errors) {
        if (row.parentCode() == null) {
            return;
        }
        if (!row.code().startsWith(row.parentCode()) || row.code().equals(row.parentCode())) {
            errors.add("parentCode", "Mã TK phải bắt đầu bằng mã TK cha " + row.parentCode() + " và dài hơn.");
        } else if (!rows.containsKey(row.parentCode())
                && repository.findByCompanyIdAndCode(companyId, row.parentCode()).filter(Account::isActive).isEmpty()) {
            errors.add("parentCode", "Không tìm thấy TK cha '" + row.parentCode() + "' trong file hoặc hệ thống.");
        }
    }
}
