package com.company.erp.masterdata.infrastructure.persistence;

import com.company.erp.masterdata.domain.Country;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface CountryRepository extends JpaRepository<Country, String>, JpaSpecificationExecutor<Country> {
}
