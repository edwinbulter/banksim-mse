package nl.banksim.api.simulatie;

import java.time.LocalDate;

import org.springframework.stereotype.Service;

/**
 * S3.1: de beheerder zet de bank op een gekozen datum, of terug op vandaag (ADR 0013). Daarna sluit de bank de maanden
 * af die tot die dag voorbij zijn, met de rente op de spaarrekeningen (ADR 0028).
 *
 * <p>Bewust zonder transactie om het geheel (backlog fase 3.3). Elke maand van elke spaarrekening wordt in een eigen
 * transactie afgesloten, met de boeking erin, en de afsluiting is idempotent. Mislukt er een, dan staat de datum al en
 * haalt de volgende afsluiting de maand in. Eén transactie om alles zou alle spaarrekeningen tegelijk vergrendelen.
 */
@Service
class SimulatieService {

    private final SimulatiedatumRepository simulatiedatum;
    private final Maandafsluiting maandafsluiting;
    private final SimulationClock klok;

    SimulatieService(SimulatiedatumRepository simulatiedatum, Maandafsluiting maandafsluiting, SimulationClock klok) {
        this.simulatiedatum = simulatiedatum;
        this.maandafsluiting = maandafsluiting;
        this.klok = klok;
    }

    /** Een datum buiten de {@link Historie} weigert de bank in de taal van de beheerder (400, ProbleemAdvice). */
    void zet(LocalDate datum) {
        if (!Historie.FO.bevat(datum)) {
            throw new IllegalArgumentException(Historie.FO.melding());
        }
        simulatiedatum.zet(datum);
        maandafsluiting.sluitAf(klok.vandaag());
    }

    void wis() {
        simulatiedatum.wis();
        maandafsluiting.sluitAf(klok.vandaag());
    }
}
