package com.company.erp.system.application;

import com.company.erp.common.domain.RecordStatus;
import com.company.erp.system.domain.DataScopeType;
import com.company.erp.system.domain.Permission;
import com.company.erp.system.domain.User;
import com.company.erp.system.domain.UserDataScope;
import com.company.erp.system.infrastructure.persistence.BranchRepository;
import com.company.erp.system.infrastructure.persistence.UserRepository;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Nạp {@link CurrentUser} (quyền, phạm vi) cho người dùng đang hoạt động. */
@Service
public class CurrentUserLoader {

    private final UserRepository userRepository;
    private final BranchRepository branchRepository;
    private final ObjectProvider<WarehouseDirectory> warehouseDirectory;

    public CurrentUserLoader(UserRepository userRepository, BranchRepository branchRepository,
                             ObjectProvider<WarehouseDirectory> warehouseDirectory) {
        this.userRepository = userRepository;
        this.branchRepository = branchRepository;
        this.warehouseDirectory = warehouseDirectory;
    }

    /**
     * Trả người dùng nếu còn hoạt động và token mang đúng phiên bản thông tin đăng nhập hiện tại
     * (token phát hành trước lần đổi/đặt lại mật khẩu bị từ chối).
     */
    @Transactional(readOnly = true)
    public Optional<CurrentUser> loadActive(Long userId, long credentialsVersion) {
        return userRepository.findWithAuthoritiesById(userId)
                .filter(User::isActive)
                .filter(user -> user.credentialsVersion() == credentialsVersion)
                .map(this::toCurrentUser);
    }

    /** Người dùng đang hoạt động (không kiểm tra phiên đăng nhập), dùng khi xét người duyệt thay. */
    @Transactional(readOnly = true)
    public Optional<CurrentUser> findActive(Long userId) {
        return userRepository.findWithAuthoritiesById(userId).filter(User::isActive).map(this::toCurrentUser);
    }

    @Transactional(readOnly = true)
    public CurrentUser load(Long userId) {
        return userRepository.findWithAuthoritiesById(userId)
                .map(this::toCurrentUser)
                .orElseThrow(() -> new IllegalStateException("Không tìm thấy người dùng " + userId));
    }

    private CurrentUser toCurrentUser(User user) {
        Set<String> permissions = user.getRoles().stream()
                .filter(role -> role.getStatus() == RecordStatus.ACTIVE)
                .flatMap(role -> role.getPermissions().stream())
                .map(Permission::getCode)
                .collect(Collectors.toSet());
        return new CurrentUser(user.getId(), user.getUsername(), user.getFullName(), user.getCompanyId(),
                user.getDefaultBranchId(), user.isMustChangePassword(), permissions, dataScopeOf(user));
    }

    private DataScope dataScopeOf(User user) {
        Set<Long> fullCompanyIds = idsOf(user, DataScopeType.COMPANY);
        Set<Long> assignedBranchIds = idsOf(user, DataScopeType.BRANCH);

        Set<Long> branchIds = new HashSet<>(assignedBranchIds);
        Set<Long> companyIds = new HashSet<>(fullCompanyIds);
        if (!fullCompanyIds.isEmpty()) {
            branchIds.addAll(branchRepository.findIdsByCompanyIdIn(fullCompanyIds));
        }
        if (!assignedBranchIds.isEmpty()) {
            companyIds.addAll(branchRepository.findCompanyIdsByIdIn(assignedBranchIds));
        }
        Set<Long> warehouseIds = idsOf(user, DataScopeType.WAREHOUSE);
        if (!warehouseIds.isEmpty()) {
            warehouseDirectory.ifAvailable(directory -> directory.findAll(warehouseIds)
                    .forEach(warehouse -> companyIds.add(warehouse.companyId())));
        }
        return new DataScope(fullCompanyIds, companyIds, branchIds, warehouseIds, user.isOwnDataOnly());
    }

    private static Set<Long> idsOf(User user, DataScopeType type) {
        return user.getDataScopes().stream()
                .filter(scope -> scope.getScopeType() == type)
                .map(UserDataScope::getScopeId)
                .collect(Collectors.toSet());
    }
}
