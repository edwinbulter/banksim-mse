package nl.banksim.api.transactie;

import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * Waar een pagina eindigt: de datum, het tijdstip en het id van de laatste transactie, in de volgorde datum, tijdstip
 * en id aflopend (ADR 0011, 0035).
 */
record Positie(LocalDate datum, OffsetDateTime tijdstip, long id) {
}
