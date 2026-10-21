package nl.banksim.api.transactie;

import java.time.LocalDate;

/** Waar een pagina eindigt: de datum en het id van de laatste transactie, in de volgorde datum en id aflopend (ADR 0011). */
record Positie(LocalDate datum, long id) {
}
