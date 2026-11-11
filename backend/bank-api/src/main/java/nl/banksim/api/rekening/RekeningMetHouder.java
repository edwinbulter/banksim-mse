package nl.banksim.api.rekening;

import nl.banksim.domain.geld.Bedrag;
import nl.banksim.domain.rekening.Iban;

/** Een rekening met de naam van de rekeninghouder, voor de kop van het scherm (FO "Kopgedeelte"). */
record RekeningMetHouder(Soort soort, Iban iban, Bedrag saldo, String rekeninghouder) {
}
