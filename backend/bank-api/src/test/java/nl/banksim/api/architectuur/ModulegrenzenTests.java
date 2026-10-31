package nl.banksim.api.architectuur;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/**
 * Hoge cohesie, lage koppeling (skill atdd §4). De code is per feature ingedeeld (betaling, grootboek, rente, …), niet in
 * lagen: een feature mag een andere gebruiken, maar nooit in een kring. Het JSON-contract van een endpoint (een record
 * {@code *Json}, genest in de controller) blijft bij de controllers.
 */
@AnalyzeClasses(packages = "nl.banksim", importOptions = ImportOption.DoNotIncludeTests.class)
class ModulegrenzenTests {

    @ArchTest
    static final ArchRule geenKringenTussenDeFeaturesVanBankApi = slices().matching("nl.banksim.api.(*)..")
            .should().beFreeOfCycles()
            .because("een kring maakt twee features één geheel: geen van beide is nog los te begrijpen of te wijzigen");

    @ArchTest
    static final ArchRule geenKringenTussenDeFeaturesVanHetDomein = slices().matching("nl.banksim.domain.(*)..")
            .should().beFreeOfCycles()
            .because("een kring maakt twee features één geheel: geen van beide is nog los te begrijpen of te wijzigen");

    @ArchTest
    static final ArchRule hetJsonContractIsVanDeController = noClasses()
            .that().haveSimpleNameNotEndingWith("Controller").and().haveSimpleNameNotEndingWith("Json")
            .should().dependOnClassesThat().haveSimpleNameEndingWith("Json")
            .because("services en het domein werken met domeintypes; het JSON-contract blijft bij het endpoint");
}
