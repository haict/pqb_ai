package com.company.erp.system.infrastructure.persistence;

import com.company.erp.system.domain.User;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserRepository extends JpaRepository<User, Long>, JpaSpecificationExecutor<User> {

    Optional<User> findByUsername(String username);

    boolean existsByUsername(String username);

    /** Nạp kèm vai trò, quyền, phạm vi trong một lần truy vấn (tránh N+1 khi xác thực mỗi request). */
    @EntityGraph(attributePaths = {"roles", "roles.permissions", "dataScopes"})
    @Query("select u from User u where u.id = :id")
    Optional<User> findWithAuthoritiesById(@Param("id") Long id);

    /** Thống kê đăng nhập cập nhật trực tiếp: không tăng version, không đổi updated_by/updated_at. */
    @Modifying
    @Query("update User u set u.failedLoginCount = :failures, u.lockedUntil = :lockedUntil where u.id = :id")
    void recordFailedLogin(@Param("id") Long id, @Param("failures") int failures,
                           @Param("lockedUntil") Instant lockedUntil);

    @Modifying
    @Query("update User u set u.failedLoginCount = 0, u.lockedUntil = null, u.lastLoginAt = :now where u.id = :id")
    void recordSuccessfulLogin(@Param("id") Long id, @Param("now") Instant now);

    /** Mở khóa tạm (khi đặt lại mật khẩu). Xóa persistence context để lần đọc sau thấy giá trị mới. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update User u set u.failedLoginCount = 0, u.lockedUntil = null where u.id = :id")
    void resetLoginFailures(@Param("id") Long id);

    @Query("select u.id from User u where u.companyId in :companyIds")
    List<Long> findIdsByCompanyIdIn(@Param("companyIds") Collection<Long> companyIds);
}
