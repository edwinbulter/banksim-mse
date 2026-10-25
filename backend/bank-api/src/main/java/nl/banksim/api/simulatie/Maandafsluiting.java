package nl.banksim.api.simulatie;

import java.time.LocalDate;

/** ADR 0028: sluit de maanden af tot en met deze datum; voor elke afgesloten maand wordt de rente bijgeschreven. */
public interface Maandafsluiting {

    void sluitAf(LocalDate totEnMet);
}
