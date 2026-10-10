package com.company.erp.system.infrastructure.persistence;

import com.company.erp.system.domain.DocumentTypeSetting;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DocumentTypeSettingRepository extends JpaRepository<DocumentTypeSetting, String> {

    List<DocumentTypeSetting> findAllByOrderBySortOrderAscCodeAsc();
}
