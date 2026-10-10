package com.company.erp.system.application;

import com.company.erp.common.domain.RecordStatus;
import com.company.erp.common.infrastructure.persistence.Specs;
import com.company.erp.system.application.dto.UserLookupResponse;
import com.company.erp.system.domain.User;
import com.company.erp.system.infrastructure.persistence.UserRepository;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Tra cứu người dùng đang hoạt động trong các công ty của người thao tác, cho ô chọn (người được ủy quyền, người
 * duyệt, nhân viên phụ trách). Chỉ trả tên, không trả thông tin quản trị.
 */
@Service
public class UserLookupService {

    private static final int MAX_RESULTS = 50;

    private final UserRepository userRepository;

    public UserLookupService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public List<UserLookupResponse> lookup(CurrentUser actor, String keyword) {
        if (actor.dataScope().companyIds().isEmpty()) {
            return List.of();
        }
        return userRepository.findAll(Specs.allOf(
                                Specs.<User>in("companyId", actor.dataScope().companyIds()),
                                Specs.equalsIfPresent("status", RecordStatus.ACTIVE),
                                Specs.keyword(keyword, "username", "fullName")),
                        PageRequest.of(0, MAX_RESULTS, Sort.by("fullName")))
                .map(user -> new UserLookupResponse(user.getId(), user.getUsername(), user.getFullName()))
                .getContent();
    }
}
