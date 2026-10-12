package com.company.erp.accounting.application;

import com.company.erp.accounting.domain.Account;
import com.company.erp.accounting.infrastructure.persistence.AccountRepository;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * API công khai tra tài khoản kế toán cho module khác (ví dụ tài khoản tiền của quỹ, tài khoản đối ứng của phiếu thu
 * chi khác); không lộ entity ra ngoài module.
 */
@Service
public class AccountLookupService {

    /** Tài khoản kế toán; {@code postable} = tài khoản chi tiết được hạch toán trực tiếp. */
    public record AccountInfo(Long id, Long companyId, String code, String name, boolean postable, boolean active,
                              boolean partnerRequired) {

        public boolean usable() {
            return postable && active;
        }
    }

    private final AccountRepository repository;

    public AccountLookupService(AccountRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public Optional<AccountInfo> account(Long id) {
        return repository.findById(id).map(AccountLookupService::toInfo);
    }

    @Transactional(readOnly = true)
    public List<AccountInfo> accounts(Collection<Long> ids) {
        return ids.isEmpty() ? List.of() : repository.findAllById(ids).stream().map(AccountLookupService::toInfo)
                .toList();
    }

    private static AccountInfo toInfo(Account account) {
        return new AccountInfo(account.getId(), account.getCompanyId(), account.getCode(), account.getName(),
                account.isPostable(), account.isActive(), account.isPartnerRequired());
    }
}
