package nl.banksim.api.simulatie;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;

/**
 * "Vandaag" voor de bank (ADR 0013): de simulatiedatum als de beheerder die zette, anders de echte datum in
 * Europe/Amsterdam. De pod draait in UTC; zonder vaste zone zou tussen middernacht en 2 uur de vorige dag gelden.
 */
public class SimulationClock {

    static final ZoneId BANK = ZoneId.of("Europe/Amsterdam");

    private final Clock klok;
    private final SimulatiedatumRepository simulatiedatum;

    public SimulationClock(Clock klok, SimulatiedatumRepository simulatiedatum) {
        this.klok = klok;
        this.simulatiedatum = simulatiedatum;
    }

    public LocalDate vandaag() {
        return simulatiedatum.lees().orElseGet(() -> LocalDate.now(klok.withZone(BANK)));
    }
}
