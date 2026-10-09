package com.company.erp.system.infrastructure.persistence;

import com.company.erp.system.domain.LoginLog;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.repository.Repository;

/** Chỉ thêm và đọc (lọc bằng Specification); ứng dụng không sửa/xóa nhật ký. */
public interface LoginLogRepository extends Repository<LoginLog, Long>, JpaSpecificationExecutor<LoginLog> {

    LoginLog save(LoginLog loginLog);
}
