package com.company.erp.system.infrastructure.persistence;

import com.company.erp.system.domain.Role;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RoleRepository extends JpaRepository<Role, Long>, JpaSpecificationExecutor<Role> {

    boolean existsByCode(String code);

    Optional<Role> findByCode(String code);

    @EntityGraph(attributePaths = "permissions")
    @Query("select r from Role r where r.id = :id")
    Optional<Role> findWithPermissionsById(@Param("id") Long id);
}
