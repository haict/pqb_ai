package com.company.erp.system.infrastructure.persistence;

import com.company.erp.system.domain.AppSetting;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AppSettingRepository extends JpaRepository<AppSetting, String> {
}
