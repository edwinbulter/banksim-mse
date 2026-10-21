package nl.banksim.api.transactie;

import java.time.LocalDate;

import nl.banksim.domain.geld.Bedrag;

/** Een geboekte transactie; een negatief bedrag is een afschrijving. */
record Transactie(LocalDate datum, String tegenpartij, Bedrag bedrag) {
}
