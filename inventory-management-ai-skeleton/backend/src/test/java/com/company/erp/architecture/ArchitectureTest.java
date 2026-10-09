package com.company.erp.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noFields;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.repository.Repository;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.RestController;

/** Kiểm tra tự động quy tắc ranh giới module và tầng (.claude/rules/architecture.md, D-42). */
class ArchitectureTest {

    private static final String ROOT = "com.company.erp";
    private static final List<String> BUSINESS_MODULES = List.of(
            "system", "masterdata", "purchasing", "inventory", "sales",
            "tender", "finance", "accounting", "equipment", "reporting");

    private static JavaClasses classes;

    @BeforeAll
    static void importClasses() {
        classes = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages(ROOT);
    }

    @Test
    @DisplayName("Không có phụ thuộc vòng giữa các module")
    void modulesAreFreeOfCycles() {
        slices().matching(ROOT + ".(*)..").should().beFreeOfCycles().check(classes);
    }

    @Test
    @DisplayName("Module chỉ dùng API công khai (application) của module khác, không dùng domain/infrastructure")
    void modulesDoNotAccessOtherModulesInternals() {
        for (String module : BUSINESS_MODULES) {
            String modulePackage = ROOT + "." + module;
            noClasses()
                    .that().resideOutsideOfPackage(modulePackage + "..")
                    .should().dependOnClassesThat()
                    .resideInAnyPackage(modulePackage + ".domain..", modulePackage + ".infrastructure..")
                    .allowEmptyShould(true)
                    .because("module " + module + " chỉ công khai tầng application")
                    .check(classes);
        }
    }

    @Test
    @DisplayName("common không phụ thuộc module nghiệp vụ")
    void commonDoesNotDependOnBusinessModules() {
        String[] businessPackages = BUSINESS_MODULES.stream().map(m -> ROOT + "." + m + "..").toArray(String[]::new);
        noClasses().that().resideInAPackage(ROOT + ".common..")
                .should().dependOnClassesThat().resideInAnyPackage(businessPackages)
                .check(classes);
    }

    @Test
    @DisplayName("Tầng api không gọi Repository")
    void apiLayerDoesNotUseRepositories() {
        noClasses().that().resideInAPackage("..api..")
                .should().dependOnClassesThat().areAssignableTo(Repository.class)
                .check(classes);
    }

    @Test
    @DisplayName("domain không phụ thuộc api, infrastructure hay Spring MVC")
    void domainIsIndependent() {
        noClasses().that().resideInAPackage("..domain..")
                .should().dependOnClassesThat()
                .resideInAnyPackage("..api..", "..infrastructure..", "org.springframework.web..")
                .check(classes);
    }

    @Test
    @DisplayName("Controller không đặt @Transactional")
    void controllersAreNotTransactional() {
        noClasses().that().areAnnotatedWith(RestController.class)
                .should().beAnnotatedWith(Transactional.class)
                .orShould().dependOnClassesThat().areAssignableTo(Repository.class)
                .allowEmptyShould(true)
                .check(classes);
    }

    @Test
    @DisplayName("Không dùng field injection")
    void noFieldInjection() {
        noFields().should().beAnnotatedWith(Autowired.class).allowEmptyShould(true).check(classes);
    }
}
