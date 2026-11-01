package nl.banksim.api.simulatie;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * "Vandaag" voor de bank (ADR 0013): de simulatiedatum als de beheerder die zette, anders de echte datum in
 * Europe/Amsterdam. De pod draait in UTC; zonder vaste zone zou tussen middernacht en 2 uur de vorige dag gelden.
 */
public class SimulationClock {

    /** De klok van de bank: datums en tijdstippen gelden in deze zone, ook al draait de pod in UTC. */
    public static final ZoneId BANK = ZoneId.of("Europe/Amsterdam");

    private final Clock klok;
    private final SimulatiedatumRepository simulatiedatum;

    public SimulationClock(Clock klok, SimulatiedatumRepository simulatiedatum) {
        this.klok = klok;
        this.simulatiedatum = simulatiedatum;
    }

    /**
     * Het tijdstip voor de bank: "vandaag" met de huidige tijd, in Europe/Amsterdam (S7.1, FO). De klok wordt één keer
     * gelezen: twee keer lezen kon rond middernacht de datum van vóór en de tijd van na middernacht geven, bijna een dag
     * te vroeg (backlog fase 7.2).
     */
    public LocalDateTime nu() {
        LocalDateTime echt = LocalDateTime.now(klok.withZone(BANK));
        return simulatiedatum.lees().map(datum -> datum.atTime(echt.toLocalTime())).orElse(echt);
    }

    public LocalDate vandaag() {
        return simulatiedatum.lees().orElseGet(() -> LocalDate.now(klok.withZone(BANK)));
    }
}
