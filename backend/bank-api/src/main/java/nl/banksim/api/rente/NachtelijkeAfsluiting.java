package nl.banksim.api.rente;

import nl.banksim.api.simulatie.Maandafsluiting;
import nl.banksim.api.simulatie.SimulationClock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** ADR 0028: de nachtelijke maandafsluiting. */
@Component
class NachtelijkeAfsluiting {

    private final Maandafsluiting maandafsluiting;
    private final SimulationClock klok;

    NachtelijkeAfsluiting(Maandafsluiting maandafsluiting, SimulationClock klok) {
        this.maandafsluiting = maandafsluiting;
        this.klok = klok;
    }

    /** Om 00:05 in Europe/Amsterdam: de maand van gisteren is dan voorbij. */
    @Scheduled(cron = "0 5 0 * * *", zone = "Europe/Amsterdam")
    void draai() {
        maandafsluiting.sluitAf(klok.vandaag());
    }
}
