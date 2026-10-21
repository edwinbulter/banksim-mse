package nl.banksim.api.rekening;

import nl.banksim.domain.geld.Bedrag;
import nl.banksim.domain.rekening.Iban;

/** Een rekening in het overzicht. Het saldo is het openingssaldo plus de geboekte transacties. */
record Rekening(Soort soort, Iban iban, Bedrag saldo) {
}
