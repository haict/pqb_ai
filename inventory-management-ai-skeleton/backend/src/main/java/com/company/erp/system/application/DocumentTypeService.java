package com.company.erp.system.application;

import com.company.erp.system.application.dto.DocumentTypeResponse;
import com.company.erp.system.domain.DocumentTypeSetting;
import com.company.erp.system.infrastructure.persistence.DocumentTypeSettingRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Danh mục loại chứng từ chỉ đọc (dùng chung mọi công ty, P1 vận hành một công ty, D-23). Từ D-61 không còn cấu hình
 * chế độ duyệt. Danh mục nhỏ, không phân trang.
 */
@Service
public class DocumentTypeService {

    private final DocumentTypeSettingRepository repository;

    public DocumentTypeService(DocumentTypeSettingRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public List<DocumentTypeResponse> list() {
        return repository.findAllByOrderBySortOrderAscCodeAsc().stream().map(DocumentTypeService::toResponse).toList();
    }

    private static DocumentTypeResponse toResponse(DocumentTypeSetting type) {
        return new DocumentTypeResponse(type.getCode(), type.getModule(), type.getName(), type.getVersion(),
                type.getUpdatedAt());
    }
}
