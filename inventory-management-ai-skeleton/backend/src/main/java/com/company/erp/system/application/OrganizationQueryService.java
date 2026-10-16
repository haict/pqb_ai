package com.company.erp.system.application;

import com.company.erp.common.domain.RecordStatus;
import com.company.erp.system.domain.Branch;
import com.company.erp.system.domain.Company;
import com.company.erp.system.domain.User;
import com.company.erp.system.infrastructure.persistence.BranchRepository;
import com.company.erp.system.infrastructure.persistence.CompanyRepository;
import com.company.erp.system.infrastructure.persistence.UserRepository;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** API công khai cho module khác tra cứu công ty, chi nhánh, người dùng (không lộ entity của module system). */
@Service
public class OrganizationQueryService {

    /** Công ty cho đầu trang mẫu in chứng từ. */
    public record CompanyRef(Long id, String code, String name, String taxCode, String address) {
    }

    /** Chi nhánh tối thiểu cho kiểm tra tham chiếu và hiển thị. */
    public record BranchRef(Long id, Long companyId, String code, String name, boolean active) {
    }

    /** Người dùng tối thiểu cho kiểm tra tham chiếu và hiển thị. */
    public record UserRef(Long id, Long companyId, String username, String fullName, boolean active) {
    }

    private final CompanyRepository companyRepository;
    private final BranchRepository branchRepository;
    private final UserRepository userRepository;

    public OrganizationQueryService(CompanyRepository companyRepository, BranchRepository branchRepository,
                                    UserRepository userRepository) {
        this.companyRepository = companyRepository;
        this.branchRepository = branchRepository;
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public Optional<CompanyRef> company(Long id) {
        return id == null ? Optional.empty() : companyRepository.findById(id).map(OrganizationQueryService::toRef);
    }

    /** Công ty đang hoạt động trong danh sách cho trước, theo mã (ô chọn công ty khi in chứng từ). */
    @Transactional(readOnly = true)
    public List<CompanyRef> activeCompanies(Collection<Long> ids) {
        return companyRepository.findAllById(ids).stream()
                .filter(company -> company.getStatus() == RecordStatus.ACTIVE)
                .sorted(Comparator.comparing(Company::getCode))
                .map(OrganizationQueryService::toRef)
                .toList();
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

    private static CompanyRef toRef(Company company) {
        return new CompanyRef(company.getId(), company.getCode(), company.getName(), company.getTaxCode(),
                company.getAddress());
    }

    private static BranchRef toRef(Branch branch) {
        return new BranchRef(branch.getId(), branch.getCompanyId(), branch.getCode(), branch.getName(),
                branch.getStatus() == RecordStatus.ACTIVE);
    }

    private static UserRef toRef(User user) {
        return new UserRef(user.getId(), user.getCompanyId(), user.getUsername(), user.getFullName(), user.isActive());
    }
}
