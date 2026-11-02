package nl.banksim.api.simulatie;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * S10.2 (FO "Admin scherm"): de dagen waarop de beheerder de bank kan zetten, van de eerste dag van de fake data t/m de
 * laatste gegenereerde dag. Tot de generator van S11.1 er is, zijn dat de vaste datums uit het FO.
 */
record Historie(LocalDate eerste, LocalDate laatste) {

    static final Historie FO = new Historie(LocalDate.of(2021, 10, 1), LocalDate.of(2026, 12, 31));

    private static final DateTimeFormatter WEERGAVE = DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.forLanguageTag("nl"));

    boolean bevat(LocalDate datum) {
        return !datum.isBefore(eerste) && !datum.isAfter(laatste);
    }

    /** "Kies een datum van 1 oktober 2021 t/m 31 december 2026." */
    String melding() {
        return "Kies een datum van " + eerste.format(WEERGAVE) + " t/m " + laatste.format(WEERGAVE) + ".";
    }
}
