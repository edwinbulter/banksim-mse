package nl.banksim.api.betaling;

import java.util.List;
import java.util.Optional;

import nl.banksim.domain.grootboek.Contact;
import nl.banksim.domain.rekening.Iban;

/** S7.1: de contactenlijst van de bank; alleen naar een IBAN uit deze lijst mag een klant betalen (FO). */
interface Contacten {

    Optional<Contact> vind(Iban iban);

    /**
     * Hoogstens 10 suggesties op een deel van de naam of het begin van het IBAN, zonder de eigen rekeningen van de
     * gebruiker (FO).
     */
    List<Contact> zoek(String tekst, String gebruikersnaam);
}
