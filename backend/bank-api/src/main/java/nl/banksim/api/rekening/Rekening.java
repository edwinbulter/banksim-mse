package nl.banksim.api.rekening;

import nl.banksim.domain.geld.Bedrag;
import nl.banksim.domain.rekening.Iban;

/** Een rekening in het overzicht. Het saldo is voorlopig het openingssaldo; boekingen komen in slice 2. */
record Rekening(Soort soort, Iban iban, Bedrag saldo) {
}
