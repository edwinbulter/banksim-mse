package nl.banksim.api.transactie;

import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;

import nl.banksim.api.sql.Like;
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
            throw new OngeldigZoekfilter(ZoekProbleem.ONGELDIG_BEDRAGBEREIK, "Bedrag van mag niet groter zijn dan Bedrag t/m");
        }
        return new Zoekfilter(ingevuld(tekst), vanaf, totEnMet,
                ingevuld(type).map(naam -> uitLijst(TransactieType.values(), naam, ZoekProbleem.ONGELDIG_TYPE)),
                ingevuld(richting).map(naam -> uitLijst(Richting.values(), naam, ZoekProbleem.ONGELDIGE_RICHTING)));
    }

    /** "albert" → "%albert%", met %, _ en \ als gewone tekens (ESCAPE '\' in de query). */
    Optional<String> tekstPatroon() {
        return tekst.map(Zoekfilter::bevat);
    }

    /** Het IBAN mag met spaties en in kleine letters: "nl91 abna" → "%NL91ABNA%". */
    Optional<String> ibanPatroon() {
        return tekst.map(waarde -> bevat(waarde.replace(" ", "").toUpperCase(Locale.ROOT)));
    }

    /**
     * FO "Naam, bedrag, IBAN of omschrijving": staat er een bedrag in het tekstveld, met punt of komma en hoogstens twee
     * decimalen, dan zoekt de bank daar precies op, als absolute waarde. Andere tekst is gewoon tekst, geen fout.
     */
    Optional<Bedrag> tekstAlsBedrag() {
        return tekst.flatMap(waarde -> {
            try {
                return Optional.of(Bedrag.van(waarde.replace(',', '.'))).map(bedrag -> new Bedrag(bedrag.waarde().abs()));
            } catch (IllegalArgumentException e) {
                return Optional.empty();
            }
        });
    }

    private static String bevat(String tekst) {
        return "%" + Like.letterlijk(tekst) + "%";
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
        throw new OngeldigZoekfilter(ZoekProbleem.ONGELDIG_BEDRAG, "Geen geldig bedrag: " + invoer);
    }

    private static <T extends Enum<T>> T uitLijst(T[] waarden, String naam, ZoekProbleem probleem) {
        return Arrays.stream(waarden).filter(waarde -> waarde.name().equalsIgnoreCase(naam)).findFirst()
                .orElseThrow(() -> new OngeldigZoekfilter(probleem, "Onbekende waarde: " + naam));
    }
}
