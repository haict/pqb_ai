package com.company.erp.system.application;

import com.company.erp.common.api.PageRequests;
import com.company.erp.common.infrastructure.persistence.Specs;
import com.company.erp.system.application.dto.LoginLogResponse;
import com.company.erp.system.domain.LoginLog;
import com.company.erp.system.domain.LoginLog.LoginEvent;
import com.company.erp.system.infrastructure.persistence.LoginLogRepository;
import com.company.erp.system.infrastructure.persistence.UserRepository;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Tra cứu nhật ký đăng nhập (BR-SYS-20) của người dùng thuộc công ty mà người xem có phạm vi toàn công ty.
 * Lần đăng nhập bằng tên không tồn tại (không gắn người dùng) chỉ hiện với người có phạm vi toàn công ty.
 */
@Service
public class LoginLogQueryService {

    private static final Set<String> SORTABLE = Set.of("occurredAt");

    private final LoginLogRepository loginLogRepository;
    private final UserRepository userRepository;

    public LoginLogQueryService(LoginLogRepository loginLogRepository, UserRepository userRepository) {
        this.loginLogRepository = loginLogRepository;
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public Page<LoginLogResponse> search(CurrentUser actor, String username, LoginEvent event, Instant from,
                                         Instant to, Pageable pageable) {
        Set<Long> companies = actor.dataScope().fullCompanyIds();
        List<Long> visibleUserIds = companies.isEmpty() ? List.of() : userRepository.findIdsByCompanyIdIn(companies);
        Specification<LoginLog> visible = (root, query, cb) -> companies.isEmpty()
                ? cb.disjunction()
                : cb.or(cb.isNull(root.get("userId")), visibleUserIds.isEmpty()
                        ? cb.disjunction() : root.get("userId").in(visibleUserIds));
        Specification<LoginLog> fromSpec = from == null ? null
                : (root, query, cb) -> cb.greaterThanOrEqualTo(root.get("occurredAt"), from);
        Specification<LoginLog> toSpec = to == null ? null
                : (root, query, cb) -> cb.lessThan(root.get("occurredAt"), to);
        return loginLogRepository.findAll(Specs.allOf(
                        visible,
                        Specs.keyword(username, "username"),
                        Specs.equalsIfPresent("event", event),
                        fromSpec,
                        toSpec),
                PageRequests.checked(pageable, SORTABLE, Sort.by(Sort.Direction.DESC, "occurredAt")))
                .map(SystemDtoMapper::toResponse);
    }
}
