package nl.banksim.api.simulatie;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

/** S3.1, ADR 0013 en 0028: na een nieuwe simulatiedatum sluit de bank de maanden af tot en met de dag van de bank. */
class SimulatieServiceTests {

    final GeheugenSimulatiedatum simulatiedatum = new GeheugenSimulatiedatum();
    final List<String> stappen = new ArrayList<>();
    final SimulatieService simulatie = new SimulatieService(simulatiedatum,
            totEnMet -> stappen.add("afgesloten tot en met " + totEnMet + ", datum " + simulatiedatum.lees().orElse(null)),
            new SimulationClock(Clock.fixed(Instant.parse("2026-10-22T12:00:00Z"), ZoneId.of("UTC")), simulatiedatum));

    @Test
    void zetEerstDeDatumEnSluitDaarnaAfTotEnMetDieDatum() {
        simulatie.zet(LocalDate.of(2026, 2, 1));

        assertThat(stappen).containsExactly("afgesloten tot en met 2026-02-01, datum 2026-02-01");
    }

    @Test
    void wistEerstDeDatumEnSluitDaarnaAfTotEnMetVandaag() {
        simulatiedatum.zet(LocalDate.of(2026, 2, 1));

        simulatie.wis();

        assertThat(stappen).containsExactly("afgesloten tot en met 2026-10-22, datum null");
        assertThat(simulatiedatum.lees()).isEqualTo(Optional.empty());
    }
}
