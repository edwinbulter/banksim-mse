package nl.banksim.api.transactie;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Optional;

import nl.banksim.domain.geld.Bedrag;
import nl.banksim.domain.transactie.TransactieType;

/**
 * Een geboekte transactie op de eigen rekening; een negatief bedrag is een afschrijving. De datum is de uitvoerdatum,
 * het tijdstip het moment van de transactie (S4.1). Omschrijving en betalingskenmerk alleen als ze er zijn (S7.1, FO).
 */
record Transactie(LocalDate datum, Bedrag bedrag, Partij eigen, Partij tegen, TransactieType type,
        OffsetDateTime tijdstip, Optional<String> omschrijving, Optional<String> betalingskenmerk) {

    String tegenpartij() {
        return tegen.naam();
    }

    /** Bij een afschrijving gaat het geld van de eigen rekening naar de tegenpartij; bij een bijschrijving andersom. */
    Partij van() {
        return isAfschrijving() ? eigen : tegen;
    }

    Partij naar() {
        return isAfschrijving() ? tegen : eigen;
    }

    // Een bedrag van 0 bestaat niet (CHECK bedrag <> 0 in V2); de mutant "< 0 wordt <= 0" is daardoor gelijkwaardig.
    private boolean isAfschrijving() {
        return bedrag.waarde().signum() < 0;
    }
}
