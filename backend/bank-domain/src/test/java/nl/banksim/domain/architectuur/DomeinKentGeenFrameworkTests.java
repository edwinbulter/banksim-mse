package nl.banksim.domain.architectuur;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/**
 * Het domein kent geen framework (skill atdd §4). De pom van bank-domain heeft geen Spring; deze regel houdt dat zo als
 * iemand er toch een framework aan toevoegt.
 */
@AnalyzeClasses(packages = "nl.banksim.domain", importOptions = ImportOption.DoNotIncludeTests.class)
class DomeinKentGeenFrameworkTests {

    @ArchTest
    static final ArchRule geenFramework = noClasses()
            .should().dependOnClassesThat()
            .resideInAnyPackage("org.springframework..", "jakarta..", "javax..", "com.fasterxml.jackson..")
            .because("de domeinregels zijn gewone Java: los te testen en te begrijpen zonder framework");
}
