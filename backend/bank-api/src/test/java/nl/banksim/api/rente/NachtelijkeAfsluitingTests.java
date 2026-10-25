package nl.banksim.api.rente;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

import nl.banksim.api.simulatie.GeheugenSimulatiedatum;
import nl.banksim.api.simulatie.SimulationClock;
import org.junit.jupiter.api.Test;

/** ADR 0028: elke nacht sluit de bank de maanden af tot en met "vandaag" voor de bank (ADR 0013). */
class NachtelijkeAfsluitingTests {

    private final List<LocalDate> afgesloten = new ArrayList<>();
    private final GeheugenSimulatiedatum simulatiedatum = new GeheugenSimulatiedatum();
    private final SimulationClock klok =
            new SimulationClock(Clock.fixed(Instant.parse("2026-11-01T00:05:00Z"), ZoneId.of("UTC")), simulatiedatum);
    private final NachtelijkeAfsluiting nacht = new NachtelijkeAfsluiting(afgesloten::add, klok);

    @Test
    void sluitAfTotEnMetVandaagVoorDeBank() {
        nacht.draai();

        assertThat(afgesloten).containsExactly(LocalDate.of(2026, 11, 1));
    }

    @Test
    void metEenSimulatiedatumIsDatVandaag() {
        simulatiedatum.zet(LocalDate.of(2026, 2, 1));

        nacht.draai();

        assertThat(afgesloten).containsExactly(LocalDate.of(2026, 2, 1));
    }
}
