package nl.banksim.api.betaling;

import nl.banksim.domain.rekening.Iban;

/** S7.1: de contactenlijst van de bank; alleen naar een IBAN uit deze lijst mag een klant betalen (FO). */
interface Contacten {

    boolean bevat(Iban iban);
}
