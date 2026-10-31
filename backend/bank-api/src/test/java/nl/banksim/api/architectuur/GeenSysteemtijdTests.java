package nl.banksim.api.architectuur;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.Year;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.ZonedDateTime;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/**
 * ADR 0013: "vandaag" komt van de SimulationClock. Een {@code now()} zonder {@code Clock} leest de systeemtijd, en
 * negeert dus de simulatiedatum.
 */
@AnalyzeClasses(packages = "nl.banksim", importOptions = ImportOption.DoNotIncludeTests.class)
class GeenSysteemtijdTests {

    /** Zowel {@code now()} als {@code now(ZoneId)}: allebei lezen ze de systeemtijd. Alleen {@code now(Clock)} mag. */
    @ArchTest
    static final ArchRule geenNowZonderClock = noClasses().should()
            .callMethod(LocalDate.class, "now")
            .orShould().callMethod(LocalDate.class, "now", ZoneId.class)
            .orShould().callMethod(LocalDateTime.class, "now")
            .orShould().callMethod(LocalDateTime.class, "now", ZoneId.class)
            .orShould().callMethod(ZonedDateTime.class, "now")
            .orShould().callMethod(ZonedDateTime.class, "now", ZoneId.class)
            .orShould().callMethod(OffsetDateTime.class, "now")
            .orShould().callMethod(OffsetDateTime.class, "now", ZoneId.class)
            .orShould().callMethod(LocalTime.class, "now")
            .orShould().callMethod(LocalTime.class, "now", ZoneId.class)
            .orShould().callMethod(YearMonth.class, "now")
            .orShould().callMethod(YearMonth.class, "now", ZoneId.class)
            .orShould().callMethod(Year.class, "now")
            .orShould().callMethod(Year.class, "now", ZoneId.class)
            .orShould().callMethod(Instant.class, "now")
            .orShould().callMethod(System.class, "currentTimeMillis")
            .because("de simulatiedatum geldt voor de hele bank (ADR 0013); gebruik de SimulationClock");

    /** Alleen {@code KlokConfig} maakt de systeemklok, voor de SimulationClock. */
    @ArchTest
    static final ArchRule deSysteemklokAlleenInKlokConfig = noClasses().that().doNotHaveSimpleName("KlokConfig").should()
            .callMethod(Clock.class, "system", ZoneId.class)
            .orShould().callMethod(Clock.class, "systemUTC")
            .orShould().callMethod(Clock.class, "systemDefaultZone")
            .because("de simulatiedatum geldt voor de hele bank (ADR 0013); gebruik de SimulationClock");
}
