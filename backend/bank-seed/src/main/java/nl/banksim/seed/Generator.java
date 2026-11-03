package nl.banksim.seed;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

import nl.banksim.domain.geld.Bedrag;
import nl.banksim.domain.grootboek.NooitRoodRegel;
import nl.banksim.domain.grootboek.SaldoVerloop;
import nl.banksim.domain.transactie.TransactieType;

/**
 * ADR 0041, FO "Transactiepatroon per maand": de boekingen van een demo-klant, van oktober 2021 t/m december 2026.
 * Voorlopig alleen de vaste bedragen, dus zonder toeval: de woonlasten op de 1e, vroeg in de ochtend, en het inkomen op
 * de 24e. Eenzijdig: de tegenpartij heeft geen rekening bij BankSim (ADR 0024).
 */
final class Generator {

    private static final YearMonth EERSTE_MAAND = YearMonth.of(2021, 10);
    private static final YearMonth LAATSTE_MAAND = YearMonth.of(2026, 12);
    private static final Bedrag GEEN_BEDRAG = Bedrag.van("0.00");

    private Generator() {
    }

    static List<Boeking> boekingen(DemoKlanten.DemoKlant klant) {
        List<Boeking> boekingen = new ArrayList<>();
        for (YearMonth maand = EERSTE_MAAND; !maand.isAfter(LAATSTE_MAAND); maand = maand.plusMonths(1)) {
            YearMonth deze = maand;
            klant.woonlasten().ifPresent(woonlasten -> boekingen.add(new Boeking(deze.atDay(1).atTime(LocalTime.of(6, 0)),
                    new Bedrag(woonlasten.bedrag().waarde().negate()), woonlasten.tegenpartij(), TransactieType.INCASSO,
                    Optional.empty())));
            boekingen.add(new Boeking(maand.atDay(24).atTime(LocalTime.of(7, 0)), klant.inkomen().bedrag(),
                    klant.inkomen().tegenpartij(), TransactieType.OVERSCHRIJVING, Optional.empty()));
        }
        controleerNooitRood(klant, boekingen);
        return boekingen;
    }

    /** FO, ADR 0025: dezelfde regel als in de bank, over het saldo aan het eind van elke dag vanaf het beginsaldo. */
    private static void controleerNooitRood(DemoKlanten.DemoKlant klant, List<Boeking> boekingen) {
        Map<LocalDate, BigDecimal> perDag = new TreeMap<>();
        boekingen.forEach(boeking -> perDag.merge(boeking.datum(), boeking.bedrag().waarde(), BigDecimal::add));
        var verloop = new SaldoVerloop(klant.beginsaldo(), perDag.values().stream().map(Bedrag::new).toList());
        if (!NooitRoodRegel.staatToe(verloop, GEEN_BEDRAG)) {
            throw new IllegalStateException("De boekingen van " + klant.gebruikersnaam() + " zouden de rekening rood maken");
        }
    }
}
