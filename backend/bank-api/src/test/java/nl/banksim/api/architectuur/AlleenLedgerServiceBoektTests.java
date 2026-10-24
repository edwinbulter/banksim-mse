package nl.banksim.api.architectuur;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import nl.banksim.api.grootboek.Grootboek;
import nl.banksim.api.grootboek.LedgerService;
import nl.banksim.domain.grootboek.Overboeking;

/** ADR 0024: LedgerService is de enige plek waar geld wordt geboekt. */
@AnalyzeClasses(packages = "nl.banksim", importOptions = ImportOption.DoNotIncludeTests.class)
class AlleenLedgerServiceBoektTests {

    @ArchTest
    static final ArchRule alleenLedgerServiceSchrijftInHetGrootboek = noClasses()
            .that().doNotBelongToAnyOf(LedgerService.class)
            .should().callMethod(Grootboek.class, "schrijf", Overboeking.class)
            .because("elke geldbeweging loopt via LedgerService.boek (ADR 0024)");
}
