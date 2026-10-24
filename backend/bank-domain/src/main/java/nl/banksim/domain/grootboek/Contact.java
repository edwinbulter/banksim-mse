package nl.banksim.domain.grootboek;

import java.util.Locale;

import nl.banksim.domain.rekening.Iban;

/** S7.1: een IBAN uit de contactenlijst van de bank, met de naam waaronder de klant ernaar mag betalen (FO). */
public record Contact(Iban iban, String naam) {

    /** Hoofdletters en extra spaties tellen niet mee. */
    public boolean naamPast(String opgegeven) {
        return opgegeven != null && normaliseer(naam).equals(normaliseer(opgegeven));
    }

    private static String normaliseer(String tekst) {
        return tekst.strip().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }
}
