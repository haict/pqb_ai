package com.company.erp.system.application;

import com.company.erp.common.domain.RecordStatus;
import com.company.erp.system.domain.Branch;
import com.company.erp.system.domain.User;
import com.company.erp.system.infrastructure.persistence.BranchRepository;
import com.company.erp.system.infrastructure.persistence.UserRepository;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** API công khai cho module khác tra cứu chi nhánh, người dùng (không lộ entity của module system). */
@Service
public class OrganizationQueryService {

    /** Chi nhánh tối thiểu cho kiểm tra tham chiếu và hiển thị. */
    public record BranchRef(Long id, Long companyId, String code, String name, boolean active) {
    }

    /** Người dùng tối thiểu cho kiểm tra tham chiếu và hiển thị. */
    public record UserRef(Long id, Long companyId, String username, String fullName, boolean active) {
    }

    private final BranchRepository branchRepository;
    private final UserRepository userRepository;

    public OrganizationQueryService(BranchRepository branchRepository, UserRepository userRepository) {
        this.branchRepository = branchRepository;
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public Optional<BranchRef> branch(Long id) {
        return id == null ? Optional.empty() : branchRepository.findById(id).map(OrganizationQueryService::toRef);
    }

    @Transactional(readOnly = true)
    public List<BranchRef> branches(Collection<Long> ids) {
        return branchRepository.findAllById(ids).stream().map(OrganizationQueryService::toRef).toList();
    }

    @Transactional(readOnly = true)
    public Optional<UserRef> user(Long id) {
        return id == null ? Optional.empty() : userRepository.findById(id).map(OrganizationQueryService::toRef);
    }

    @Transactional(readOnly = true)
    public List<UserRef> users(Collection<Long> ids) {
        return userRepository.findAllById(ids).stream().map(OrganizationQueryService::toRef).toList();
    }

    private static BranchRef toRef(Branch branch) {
        return new BranchRef(branch.getId(), branch.getCompanyId(), branch.getCode(), branch.getName(),
                branch.getStatus() == RecordStatus.ACTIVE);
    }

    private static UserRef toRef(User user) {
        return new UserRef(user.getId(), user.getCompanyId(), user.getUsername(), user.getFullName(), user.isActive());
    }
}
