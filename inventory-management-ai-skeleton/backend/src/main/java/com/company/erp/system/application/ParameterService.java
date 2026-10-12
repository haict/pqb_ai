package com.company.erp.system.application;

import com.company.erp.common.application.AuditEntry;
import com.company.erp.common.application.AuditLogService;
import com.company.erp.common.domain.BusinessException;
import com.company.erp.common.domain.ErrorCode;
import com.company.erp.system.application.dto.ParameterResponse;
import com.company.erp.system.application.dto.UpdateParameterRequest;
import com.company.erp.system.domain.Parameter;
import com.company.erp.system.domain.ParameterDefinition;
import com.company.erp.system.infrastructure.persistence.CompanyRepository;
import com.company.erp.system.infrastructure.persistence.ParameterDefinitionRepository;
import com.company.erp.system.infrastructure.persistence.ParameterRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Tham số hệ thống theo công ty (BR-SYS-13). Thay đổi ghi audit giá trị trước/sau; tham số ảnh hưởng số liệu đã
 * ghi sổ bị khóa khi công ty đã phát sinh số liệu ghi sổ ({@link PostingActivityProbe}).
 */
@Service
public class ParameterService {

    private final ParameterDefinitionRepository definitionRepository;
    private final ParameterRepository parameterRepository;
    private final CompanyRepository companyRepository;
    private final ObjectProvider<PostingActivityProbe> postingProbes;
    private final AuditLogService auditLog;

    public ParameterService(ParameterDefinitionRepository definitionRepository,
                            ParameterRepository parameterRepository, CompanyRepository companyRepository,
                            ObjectProvider<PostingActivityProbe> postingProbes, AuditLogService auditLog) {
        this.definitionRepository = definitionRepository;
        this.parameterRepository = parameterRepository;
        this.companyRepository = companyRepository;
        this.postingProbes = postingProbes;
        this.auditLog = auditLog;
    }

    /** Giá trị số nguyên của tham số cho công ty: giá trị riêng nếu đã đặt, ngược lại giá trị mặc định. */
    @Transactional(readOnly = true)
    public int integerValue(Long companyId, String key) {
        return Integer.parseInt(rawValue(companyId, key));
    }

    /** Giá trị thập phân (ngưỡng tiền) của tham số cho công ty: giá trị riêng nếu đã đặt, ngược lại mặc định. */
    @Transactional(readOnly = true)
    public BigDecimal decimalValue(Long companyId, String key) {
        return new BigDecimal(rawValue(companyId, key));
    }

    private String rawValue(Long companyId, String key) {
        return parameterRepository.findByCompanyIdAndKey(companyId, key)
                .map(Parameter::getValue)
                .orElseGet(() -> definitionRepository.findById(key)
                        .map(ParameterDefinition::getDefaultValue)
                        .orElseThrow(() -> new IllegalStateException("Tham số chưa được khai báo: " + key)));
    }

    /** Toàn bộ tham số của công ty (danh mục nhỏ, cố định theo migration, không phân trang). */
    @Transactional(readOnly = true)
    public List<ParameterResponse> list(CurrentUser actor, Long companyId) {
        requireVisibleCompany(actor, companyId);
        Map<String, Parameter> values = parameterRepository.findByCompanyId(companyId).stream()
                .collect(Collectors.toMap(Parameter::getKey, Function.identity()));
        boolean posted = hasPostedData(companyId);
        return definitionRepository.findAllByOrderBySortOrderAscKeyAsc().stream()
                .map(definition -> toResponse(definition, Optional.ofNullable(values.get(definition.getKey())),
                        posted))
                .toList();
    }

    @Transactional
    public ParameterResponse update(CurrentUser actor, String key, UpdateParameterRequest request) {
        Long companyId = request.companyId();
        requireVisibleCompany(actor, companyId);
        if (!actor.dataScope().hasFullCompanyAccess(companyId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "Cần phạm vi dữ liệu toàn công ty để sửa tham số.");
        }
        ParameterDefinition definition = definitionRepository.findById(key).orElseThrow(BusinessException::notFound);
        boolean posted = hasPostedData(companyId);
        if (definition.isLockAfterPosting() && posted) {
            throw new BusinessException(ErrorCode.PARAMETER_LOCKED,
                    "Tham số \"" + definition.getName() + "\" đã khóa vì công ty đã phát sinh số liệu ghi sổ.");
        }
        String value = definition.normalize(request.value());
        Optional<Parameter> existing = parameterRepository.findByCompanyIdAndKey(companyId, key);
        ParameterResponse before = toResponse(definition, existing, posted);
        Parameter parameter = existing
                .map(current -> {
                    current.verifyVersion(request.version() - 1);
                    current.changeValue(value);
                    return current;
                })
                .orElseGet(() -> newParameter(companyId, key, value, request.version()));
        ParameterResponse after = toResponse(definition, Optional.of(save(parameter)), posted);
        auditLog.record(new AuditEntry(actor.id(), AuditActions.UPDATE, AuditActions.PARAMETER, parameter.getId(),
                null, null, before, after, companyId, null));
        return after;
    }

    private static Parameter newParameter(Long companyId, String key, String value, int expectedVersion) {
        // Chưa có giá trị riêng: client phải đang giữ version 0; khác 0 là dữ liệu client đã cũ.
        if (expectedVersion != 0) {
            throw new BusinessException(ErrorCode.CONCURRENT_MODIFICATION);
        }
        return new Parameter(companyId, key, value);
    }

    private Parameter save(Parameter parameter) {
        try {
            return parameterRepository.saveAndFlush(parameter);
        } catch (DataIntegrityViolationException ex) {
            // Hai người cùng đặt giá trị riêng lần đầu: unique (company_id, param_key) chặn người thứ hai.
            throw new BusinessException(ErrorCode.CONCURRENT_MODIFICATION);
        }
    }

    private boolean hasPostedData(Long companyId) {
        return postingProbes.orderedStream().anyMatch(probe -> probe.hasPostedData(companyId));
    }

    private void requireVisibleCompany(CurrentUser actor, Long companyId) {
        if (!actor.dataScope().canAccessCompany(companyId) || !companyRepository.existsById(companyId)) {
            throw BusinessException.notFound();
        }
    }

    private static ParameterResponse toResponse(ParameterDefinition definition, Optional<Parameter> value,
                                                boolean posted) {
        return new ParameterResponse(definition.getKey(), definition.getModule(), definition.getName(),
                definition.getDescription(), definition.getValueType(),
                value.map(Parameter::getValue).orElse(definition.getDefaultValue()), definition.getDefaultValue(),
                definition.getMinValue(), definition.getMaxValue(), definition.allowedValueList(),
                definition.isLockAfterPosting(), definition.isLockAfterPosting() && posted, value.isPresent(),
                value.map(parameter -> parameter.getVersion() + 1).orElse(0));
    }
}
