package com.company.erp.system.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.company.erp.common.application.AuditLogService;
import com.company.erp.common.domain.BusinessException;
import com.company.erp.common.domain.ErrorCode;
import com.company.erp.system.application.dto.UpdateParameterRequest;
import com.company.erp.system.domain.ParameterDefinition;
import com.company.erp.system.infrastructure.persistence.CompanyRepository;
import com.company.erp.system.infrastructure.persistence.ParameterDefinitionRepository;
import com.company.erp.system.infrastructure.persistence.ParameterRepository;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

class ParameterServiceTest {

    private static final String KEY = "decimal.amount-scale";

    private final ParameterDefinitionRepository definitions = mock(ParameterDefinitionRepository.class);
    private final ParameterRepository parameters = mock(ParameterRepository.class);
    private final CompanyRepository companies = mock(CompanyRepository.class);
    private final PostingActivityProbe probe = mock(PostingActivityProbe.class);
    private final ParameterDefinition definition = mock(ParameterDefinition.class);
    private final CurrentUser admin = new CurrentUser(1L, "admin", "Admin", 1L, 1L, false, Set.of("parameter:update"),
            new DataScope(Set.of(1L), Set.of(1L), Set.of(1L), Set.of(), false));
    private ParameterService service;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        ObjectProvider<PostingActivityProbe> probes = mock(ObjectProvider.class);
        when(probes.orderedStream()).thenAnswer(invocation -> Stream.of(probe));
        service = new ParameterService(definitions, parameters, companies, probes, mock(AuditLogService.class));
        when(companies.existsById(1L)).thenReturn(true);
        when(definitions.findById(KEY)).thenReturn(Optional.of(definition));
        when(definition.getName()).thenReturn("Số chữ số thập phân của thành tiền");
    }

    @Test
    @DisplayName("BR-SYS-13: tham số ảnh hưởng số liệu ghi sổ bị khóa khi công ty đã phát sinh số liệu ghi sổ")
    void lockedAfterPosting() {
        when(definition.isLockAfterPosting()).thenReturn(true);
        when(probe.hasPostedData(1L)).thenReturn(true);

        assertThatThrownBy(() -> service.update(admin, KEY, new UpdateParameterRequest(1L, "2", 0)))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).errorCode()).isEqualTo(ErrorCode.PARAMETER_LOCKED));
        verify(parameters, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("Chưa phát sinh số liệu ghi sổ thì vẫn sửa được")
    void editableBeforePosting() {
        when(definition.isLockAfterPosting()).thenReturn(true);
        when(definition.normalize("2")).thenReturn("2");
        when(probe.hasPostedData(1L)).thenReturn(false);
        when(parameters.findByCompanyIdAndKey(1L, KEY)).thenReturn(Optional.empty());
        when(parameters.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));

        assertThat(service.update(admin, KEY, new UpdateParameterRequest(1L, "2", 0)).value()).isEqualTo("2");
    }

    @Test
    @DisplayName("Phạm vi chi nhánh không sửa được tham số công ty")
    void requiresFullCompanyScope() {
        CurrentUser branchUser = new CurrentUser(5L, "giamdoc.hn", "GĐ HN", 1L, 1L, false,
                Set.of("parameter:update"), new DataScope(Set.of(), Set.of(1L), Set.of(1L), Set.of(), false));

        assertThatThrownBy(() -> service.update(branchUser, KEY, new UpdateParameterRequest(1L, "2", 0)))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).errorCode()).isEqualTo(ErrorCode.FORBIDDEN));
    }
}
