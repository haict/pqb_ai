package com.company.erp.system.infrastructure.persistence;

import com.company.erp.system.domain.Permission;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PermissionRepository extends JpaRepository<Permission, Long> {

    List<Permission> findAllByOrderByModuleAscSortOrderAsc();

    List<Permission> findByCodeIn(Collection<String> codes);
}
