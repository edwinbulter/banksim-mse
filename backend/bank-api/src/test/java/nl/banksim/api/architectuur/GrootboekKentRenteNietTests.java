package nl.banksim.api.architectuur;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/** ADR 0030: de rente-code hoort van een boeking via OverboekingGeboekt; het grootboek kent de rente-code niet. */
@AnalyzeClasses(packages = "nl.banksim", importOptions = ImportOption.DoNotIncludeTests.class)
class GrootboekKentRenteNietTests {

    @ArchTest
    static final ArchRule grootboekHangtNietAfVanRente = noClasses()
            .that().resideInAPackage("..grootboek..")
            .should().dependOnClassesThat().resideInAPackage("..rente..")
            .because("de rente-code luistert naar OverboekingGeboekt; anders ontstaat een kring (ADR 0030)");
}
