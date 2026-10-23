package nl.banksim.api.transactie;

import java.util.Arrays;
import java.util.Optional;

import nl.banksim.domain.geld.Bedrag;
import nl.banksim.domain.transactie.TransactieType;

/**
 * S5.1: een zoekopdracht, gecontroleerd vóór er een query is. Lege velden doen niet mee; alle ingevulde moeten gelden.
 * Bedragen gelden voor de absolute waarde, mogen een komma hebben en "van" mag niet groter zijn dan "t/m".
 */
record Zoekfilter(Optional<String> tekst, Optional<Bedrag> van, Optional<Bedrag> tm, Optional<TransactieType> type,
        Optional<Richting> richting) {

    static final Zoekfilter GEEN = new Zoekfilter(Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(),
            Optional.empty());

    /** Inkomend: bedrag > 0; uitgaand: bedrag < 0 (FO "Filteren op"). */
    enum Richting { INKOMEND, UITGAAND }

    static Zoekfilter van(Optional<String> tekst, Optional<String> van, Optional<String> tm, Optional<String> type,
            Optional<String> richting) {
        Optional<Bedrag> vanaf = ingevuld(van).map(Zoekfilter::bedrag);
        Optional<Bedrag> totEnMet = ingevuld(tm).map(Zoekfilter::bedrag);
        if (vanaf.isPresent() && totEnMet.isPresent() && vanaf.get().waarde().compareTo(totEnMet.get().waarde()) > 0) {
            throw new OngeldigZoekfilter("ongeldig-bedragbereik", "Bedrag van mag niet groter zijn dan Bedrag t/m");
        }
        return new Zoekfilter(ingevuld(tekst), vanaf, totEnMet,
                ingevuld(type).map(naam -> uitLijst(TransactieType.values(), naam, "ongeldig-type")),
                ingevuld(richting).map(naam -> uitLijst(Richting.values(), naam, "ongeldige-richting")));
    }

    /** "albert" → "%albert%", met %, _ en \ als gewone tekens (ESCAPE '\' in de query). */
    Optional<String> tekstPatroon() {
        return tekst.map(Zoekfilter::bevat);
    }

    /** Het IBAN mag met spaties en in kleine letters: "nl91 abna" → "%NL91ABNA%". */
    Optional<String> ibanPatroon() {
        return tekst.map(waarde -> bevat(waarde.replace(" ", "").toUpperCase()));
    }

    private static String bevat(String tekst) {
        return "%" + tekst.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
    }

    private static Optional<String> ingevuld(Optional<String> veld) {
        return veld.map(String::strip).filter(waarde -> !waarde.isEmpty());
    }

    private static Bedrag bedrag(String invoer) {
        try {
            Bedrag bedrag = Bedrag.van(invoer.replace(',', '.'));
            if (bedrag.waarde().signum() >= 0) {
                return bedrag;
            }
        } catch (IllegalArgumentException e) {
            // hieronder
        }
        throw new OngeldigZoekfilter("ongeldig-bedrag", "Geen geldig bedrag: " + invoer);
    }

    private static <T extends Enum<T>> T uitLijst(T[] waarden, String naam, String probleem) {
        return Arrays.stream(waarden).filter(waarde -> waarde.name().equalsIgnoreCase(naam)).findFirst()
                .orElseThrow(() -> new OngeldigZoekfilter(probleem, "Onbekende waarde: " + naam));
    }
}
