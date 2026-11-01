package nl.banksim.api.simulatie;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.concurrent.atomic.AtomicInteger;

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

    /** S7.1 (FO): een betaling krijgt de simulatiedatum met de huidige tijd, in Europe/Amsterdam (12:00 UTC is 14:00). */
    @Test
    void nuIsDeSimulatiedatumMetDeHuidigeTijdInEuropeAmsterdam() {
        opgeslagen.zet(LocalDate.of(2026, 10, 5));
        var klok = new SimulationClock(Clock.fixed(Instant.parse("2026-10-22T12:00:00Z"), ZoneId.of("UTC")), opgeslagen);

        assertThat(klok.nu()).isEqualTo(LocalDateTime.of(2026, 10, 5, 14, 0));
    }

    @Test
    void nuIsZonderSimulatiedatumHetEchteTijdstipInEuropeAmsterdam() {
        var klok = new SimulationClock(Clock.fixed(Instant.parse("2026-10-21T23:30:00Z"), ZoneId.of("UTC")), opgeslagen);

        assertThat(klok.nu()).isEqualTo(LocalDateTime.of(2026, 10, 22, 1, 30));
    }

    /**
     * Backlog fase 7.2: tikt de klok tussen twee keer aflezen over middernacht, dan hoort "nu" bij één van beide
     * momenten, niet de datum van vóór middernacht met de tijd van erna (bijna een dag te vroeg).
     */
    @Test
    void nuLeestDeKlokEenKeerOokRondMiddernacht() {
        Instant voor = Instant.parse("2026-10-21T21:59:59.999Z");
        Instant na = Instant.parse("2026-10-21T22:00:00Z");
        var klok = new SimulationClock(new TikkendeKlok(voor, na), opgeslagen);

        assertThat(klok.nu()).isIn(LocalDateTime.of(2026, 10, 21, 23, 59, 59, 999_000_000), LocalDateTime.of(2026, 10, 22, 0, 0));
    }

    /** Geeft bij elke aflezing het volgende moment, en daarna steeds het laatste; ook na withZone telt dezelfde teller. */
    private static final class TikkendeKlok extends Clock {

        private final Instant[] momenten;
        private final AtomicInteger aflezing;
        private final ZoneId zone;

        TikkendeKlok(Instant... momenten) {
            this(momenten, new AtomicInteger(), ZoneId.of("UTC"));
        }

        private TikkendeKlok(Instant[] momenten, AtomicInteger aflezing, ZoneId zone) {
            this.momenten = momenten;
            this.aflezing = aflezing;
            this.zone = zone;
        }

        @Override
        public Instant instant() {
            return momenten[Math.min(aflezing.getAndIncrement(), momenten.length - 1)];
        }

        @Override
        public ZoneId getZone() {
            return zone;
        }

        @Override
        public Clock withZone(ZoneId andereZone) {
            return new TikkendeKlok(momenten, aflezing, andereZone);
        }
    }
}
