package nl.banksim.api.simulatie;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

import org.junit.jupiter.api.Test;

/** ADR 0013: "vandaag" voor de bank is de simulatiedatum, of de echte datum in Europe/Amsterdam. */
class SimulationClockTests {

    static final ZoneId AMSTERDAM = ZoneId.of("Europe/Amsterdam");

    private final GeheugenSimulatiedatum opgeslagen = new GeheugenSimulatiedatum();

    @Test
    void geeftDeSimulatiedatumAlsDieGezetIs() {
        opgeslagen.zet(LocalDate.of(2026, 10, 5));
        var klok = new SimulationClock(Clock.fixed(Instant.parse("2026-10-22T12:00:00Z"), AMSTERDAM), opgeslagen);

        assertThat(klok.vandaag()).isEqualTo(LocalDate.of(2026, 10, 5));
    }

    @Test
    void geeftZonderSimulatiedatumDeEchteDatum() {
        var klok = new SimulationClock(Clock.fixed(Instant.parse("2026-10-22T12:00:00Z"), AMSTERDAM), opgeslagen);

        assertThat(klok.vandaag()).isEqualTo(LocalDate.of(2026, 10, 22));
    }

    /** 23:30 UTC is al 01:30 de volgende dag in Amsterdam (zomertijd); de pod draait in UTC. */
    @Test
    void rekentDeEchteDatumInEuropeAmsterdamOokAlsDeKlokInUtcStaat() {
        var klok = new SimulationClock(Clock.fixed(Instant.parse("2026-10-21T23:30:00Z"), ZoneId.of("UTC")), opgeslagen);

        assertThat(klok.vandaag()).isEqualTo(LocalDate.of(2026, 10, 22));
    }

    @Test
    void geeftNaHetWissenWeerDeEchteDatum() {
        opgeslagen.zet(LocalDate.of(2026, 10, 5));
        opgeslagen.wis();
        var klok = new SimulationClock(Clock.fixed(Instant.parse("2026-10-22T12:00:00Z"), AMSTERDAM), opgeslagen);

        assertThat(klok.vandaag()).isEqualTo(LocalDate.of(2026, 10, 22));
    }
}
