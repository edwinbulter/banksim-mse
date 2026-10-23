package nl.banksim.api.simulatie;

import java.time.LocalDate;
import java.util.Optional;

/** De simulatiedatum van de bank (ADR 0013); leeg betekent: de bank draait op vandaag. */
public interface SimulatiedatumRepository {

    Optional<LocalDate> lees();

    void zet(LocalDate datum);

    void wis();
}
