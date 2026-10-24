package nl.banksim.seed;

import nl.banksim.domain.rekening.Iban;

/** S7.1: de contactenlijst van de bank; alleen naar een IBAN uit deze lijst mag een klant betalen (FO "Betalen scherm"). */
public interface Contacten {

    void voegToe(Iban iban, String naam);
}
