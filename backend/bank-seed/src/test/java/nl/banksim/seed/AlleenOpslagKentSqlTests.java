package nl.banksim.seed;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * Hoge cohesie (skill atdd §4, backlog fase 6.4): de SQL van bank-seed staat in de opslagklassen ({@code Jdbc*}). De
 * opdrachten zelf ({@code SeedCommando}, {@code SeedKlantAanmaker}) werken via hun interfaces; alleen de applicatie
 * bouwt de opslagklassen op.
 */
@AnalyzeClasses(packages = "nl.banksim.seed", importOptions = ImportOption.DoNotIncludeTests.class)
class AlleenOpslagKentSqlTests {

    @ArchTest
    static final ArchRule alleenDeOpslagKentDeDatabase = noClasses()
            .that().haveSimpleNameNotStartingWith("Jdbc").and().haveSimpleNameNotEndingWith("Application")
            .should().dependOnClassesThat().belongToAnyOf(JdbcClient.class, JdbcTemplate.class, NamedParameterJdbcTemplate.class)
            .because("de opdrachten kennen het schema niet; dat blijft bij de opslag");
}
