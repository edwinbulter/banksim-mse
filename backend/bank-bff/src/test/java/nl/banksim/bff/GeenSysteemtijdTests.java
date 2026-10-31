package nl.banksim.bff;

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
 * ADR 0013: "vandaag" komt van de SimulationClock in bank-api. bank-bff leest de systeemtijd niet; zo blijft de
 * simulatiedatum gelden voor de hele bank. Dezelfde regels als {@code GeenSysteemtijdTests} in bank-api.
 */
@AnalyzeClasses(packages = "nl.banksim.bff", importOptions = ImportOption.DoNotIncludeTests.class)
class GeenSysteemtijdTests {

    @ArchTest
    static final ArchRule geenSysteemtijd = noClasses().should()
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
            .orShould().callMethod(Clock.class, "system", ZoneId.class)
            .orShould().callMethod(Clock.class, "systemUTC")
            .orShould().callMethod(Clock.class, "systemDefaultZone")
            .because("de simulatiedatum geldt voor de hele bank (ADR 0013)");
}
