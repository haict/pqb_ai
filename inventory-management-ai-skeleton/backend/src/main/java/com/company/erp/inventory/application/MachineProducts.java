package com.company.erp.inventory.application;

import com.company.erp.common.domain.BusinessException;
import com.company.erp.masterdata.application.MasterDataQueryService;
import com.company.erp.masterdata.application.MasterDataQueryService.ProductInfo;
import com.company.erp.system.application.CurrentUser;
import com.company.erp.system.application.ParameterService;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Nhận diện hàng thuộc nhóm máy (D-70, BR-INV-43): nhóm hàng có mã theo tham số
 * {@value #MACHINE_CATEGORY_CODE} và mọi nhóm con. Chỉ hàng nhóm máy khai báo máy mới / máy cũ khi nhập kho.
 */
@Service
public class MachineProducts {

    static final String MACHINE_CATEGORY_CODE = "inventory.machine-category-code";

    private final MasterDataQueryService masterData;
    private final ParameterService parameters;

    MachineProducts(MasterDataQueryService masterData, ParameterService parameters) {
        this.masterData = masterData;
        this.parameters = parameters;
    }

    /** ID nhóm hàng máy cho giao diện bật ô "Máy cũ" trên dòng nhập; ngoài phạm vi công ty thì 404. */
    @Transactional(readOnly = true)
    public Set<Long> categoryIds(CurrentUser actor, Long companyId) {
        if (!actor.dataScope().canAccessCompany(companyId)) {
            throw BusinessException.notFound();
        }
        return categories(companyId);
    }

    /** ID các nhóm hàng máy của công ty; tra một lần cho cả chứng từ. */
    Set<Long> categories(Long companyId) {
        return masterData.categoryTree(companyId, parameters.stringValue(companyId, MACHINE_CATEGORY_CODE));
    }

    static boolean isMachine(ProductInfo product, Set<Long> machineCategories) {
        return product.categoryId() != null && machineCategories.contains(product.categoryId());
    }
}
