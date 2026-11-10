package com.company.erp.system.infrastructure.persistence;

import com.company.erp.system.domain.UiFeature;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UiFeatureRepository extends JpaRepository<UiFeature, String> {
}
