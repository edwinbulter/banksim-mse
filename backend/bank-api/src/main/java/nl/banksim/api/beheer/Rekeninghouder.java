package nl.banksim.api.beheer;

import java.util.Optional;

import nl.banksim.domain.geld.Bedrag;
import nl.banksim.domain.rekening.Iban;

/** S10.1: een rekeninghouder zoals de beheerder hem ziet, met de saldi op een datum; de spaarrekening kan ontbreken. */
record Rekeninghouder(String naam, Iban betaalrekening, Bedrag saldo, Optional<Iban> spaarrekening,
        Optional<Bedrag> spaarsaldo) {
}
