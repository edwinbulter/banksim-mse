package nl.banksim.seed;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.TreeMap;

import nl.banksim.domain.geld.Bedrag;
import nl.banksim.domain.grootboek.NooitRoodRegel;
import nl.banksim.domain.grootboek.SaldoVerloop;
import nl.banksim.domain.transactie.TransactieType;

/**
 * ADR 0041, FO "Transactiepatroon per maand": de boekingen van een demo-klant, van oktober 2021 t/m december 2026. Eerst de
 * vaste bedragen: de woonlasten op de 1e en de vaste lasten op hun eigen dag, vroeg in de ochtend, en het inkomen op de 24e. Daarna de boodschappen, met een
 * vaste seed per klant; een boodschap die de rekening rood zou maken, vervalt (FO). Eenzijdig: de tegenpartij heeft geen
 * rekening bij BankSim (ADR 0024).
 */
final class Generator {

    private static final YearMonth EERSTE_MAAND = YearMonth.of(2021, 10);
    private static final YearMonth LAATSTE_MAAND = YearMonth.of(2026, 12);
    private static final Bedrag GEEN_BEDRAG = Bedrag.van("0.00");
    private static final long SEED = 42;
    private static final List<String> SUPERMARKTEN = List.of("BuurtSuper", "Prijsvast Supermarkt", "Versmarkt");

    private Generator() {
    }

    static List<Boeking> boekingen(DemoKlanten.DemoKlant klant) {
        List<Boeking> boekingen = vasteBoekingen(klant);
        controleerNooitRood(klant, boekingen);
        voegBoodschappenToe(klant, boekingen);
        boekingen.sort(Comparator.comparing(Boeking::tijdstip));
        return boekingen;
    }

    private static List<Boeking> vasteBoekingen(DemoKlanten.DemoKlant klant) {
        List<Boeking> boekingen = new ArrayList<>();
        for (YearMonth maand = EERSTE_MAAND; !maand.isAfter(LAATSTE_MAAND); maand = maand.plusMonths(1)) {
            YearMonth deze = maand;
            klant.woonlasten().ifPresent(woonlasten -> boekingen.add(new Boeking(deze.atDay(1).atTime(LocalTime.of(6, 0)),
                    new Bedrag(woonlasten.bedrag().waarde().negate()), woonlasten.tegenpartij(), TransactieType.INCASSO,
                    Optional.empty())));
            boekingen.add(new Boeking(maand.atDay(24).atTime(LocalTime.of(7, 0)), klant.inkomen().bedrag(),
                    klant.inkomen().tegenpartij(), TransactieType.OVERSCHRIJVING, Optional.empty()));
            for (DemoKlanten.VasteLast last : klant.vasteLasten()) {
                boekingen.add(new Boeking(maand.atDay(last.dag()).atTime(LocalTime.of(6, 0)),
                        new Bedrag(last.bedrag().waarde().negate()), last.tegenpartij(), TransactieType.INCASSO, Optional.empty()));
            }
        }
        return boekingen;
    }

    /** FO: 6–12 keer per maand, € 8–120, tussen 8:00 en 21:00. Dezelfde klant krijgt altijd dezelfde boodschappen. */
    private static void voegBoodschappenToe(DemoKlanten.DemoKlant klant, List<Boeking> boekingen) {
        Random toeval = new Random(SEED + klant.gebruikersnaam().hashCode());
        for (YearMonth maand = EERSTE_MAAND; !maand.isAfter(LAATSTE_MAAND); maand = maand.plusMonths(1)) {
            int aantal = 6 + toeval.nextInt(7);
            for (int i = 0; i < aantal; i++) {
                Boeking boodschap = boodschap(maand, toeval);
                if (past(klant, boekingen, boodschap)) {
                    boekingen.add(boodschap);
                }
            }
        }
    }

    private static Boeking boodschap(YearMonth maand, Random toeval) {
        LocalDateTime tijdstip = maand.atDay(1 + toeval.nextInt(maand.lengthOfMonth()))
                .atTime(LocalTime.of(8, 0).plusMinutes(toeval.nextInt(13 * 60 + 1)));
        BigDecimal bedrag = BigDecimal.valueOf(800 + toeval.nextInt(11_201), 2).negate();
        String supermarkt = SUPERMARKTEN.get(toeval.nextInt(SUPERMARKTEN.size()));
        return new Boeking(tijdstip, new Bedrag(bedrag), supermarkt, TransactieType.BETAALAUTOMAAT, Optional.empty());
    }

    /** FO, ADR 0025: dezelfde regel als bij een betaling in de bank, dus inclusief de latere boekingen. */
    private static boolean past(DemoKlanten.DemoKlant klant, List<Boeking> boekingen, Boeking boodschap) {
        BigDecimal saldo = klant.beginsaldo().waarde();
        Map<LocalDate, BigDecimal> later = new TreeMap<>();
        for (Boeking boeking : boekingen) {
            if (boeking.datum().isAfter(boodschap.datum())) {
                later.merge(boeking.datum(), boeking.bedrag().waarde(), BigDecimal::add);
            } else {
                saldo = saldo.add(boeking.bedrag().waarde());
            }
        }
        var verloop = new SaldoVerloop(new Bedrag(saldo), later.values().stream().map(Bedrag::new).toList());
        return NooitRoodRegel.staatToe(verloop, new Bedrag(boodschap.bedrag().waarde().negate()));
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
