package nl.banksim.api.betaling;

import java.util.Optional;

import nl.banksim.domain.grootboek.Contact;
import nl.banksim.domain.rekening.Iban;

/** S7.1: de contactenlijst van de bank; alleen naar een IBAN uit deze lijst mag een klant betalen (FO). */
interface Contacten {

    Optional<Contact> vind(Iban iban);
}
