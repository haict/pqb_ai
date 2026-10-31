package com.company.erp.masterdata.application;

import com.company.erp.common.domain.BusinessException;
import com.company.erp.common.domain.ErrorCode;
import com.company.erp.masterdata.application.dto.TaxCodeLookupResponse;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;

/**
 * Tra cứu tên, địa chỉ doanh nghiệp theo mã số thuế để gợi ý khi thêm đối tác (D-66). Chỉ trả gợi ý, không ghi dữ
 * liệu; người dùng xem lại trước khi lưu.
 */
@Service
public class TaxCodeLookupService {

    private static final Pattern TAX_CODE = Pattern.compile("^[0-9]{10}(-[0-9]{3})?$");

    private final TaxCodeLookupProperties properties;
    private final TaxCodeDirectory directory;

    public TaxCodeLookupService(TaxCodeLookupProperties properties, TaxCodeDirectory directory) {
        this.properties = properties;
        this.directory = directory;
    }

    public TaxCodeLookupResponse lookup(String taxCode) {
        String normalized = taxCode == null ? "" : taxCode.trim();
        if (!TAX_CODE.matcher(normalized).matches()) {
            throw BusinessException.invalidField("taxCode", "Mã số thuế gồm 10 số hoặc 10 số-3 số.");
        }
        if (!properties.enabled()) {
            throw new BusinessException(ErrorCode.TAX_CODE_LOOKUP_UNAVAILABLE);
        }
        return directory.lookup(normalized);
    }
}
