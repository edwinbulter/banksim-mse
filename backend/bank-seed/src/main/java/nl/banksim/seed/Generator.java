package nl.banksim.seed;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.Month;
import java.time.YearMonth;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.TreeMap;

import nl.banksim.domain.geld.Bedrag;
import nl.banksim.domain.grootboek.NooitRoodRegel;
import nl.banksim.domain.grootboek.SaldoVerloop;
import nl.banksim.domain.rente.DagSaldo;
import nl.banksim.domain.rente.RenteCalculator;
import nl.banksim.domain.transactie.TransactieType;

/**
 * ADR 0041, FO "Transactiepatroon per maand": de boekingen van een demo-klant, van oktober 2021 t/m december 2026. Eerst de
 * vaste bedragen: de woonlasten op de 1e en de vaste lasten op hun eigen dag, vroeg in de ochtend, het inkomen op de 24e, in mei met het vakantiegeld van een werknemer, en de inleg op de 25e. Daarna de boodschappen, de
 * webwinkels en de geldopnames, met een vaste seed per klant; een vaste boeking die de rekening rood zou maken, krijgt een opname, en een aankoop vervalt (FO). Eenzijdig: de tegenpartij heeft geen
 * rekening bij BankSim (ADR 0024).
 */
final class Generator {

    private static final YearMonth EERSTE_MAAND = YearMonth.of(2021, 10);
    /** ADR 0043: de eerste dag van de historie; de maandafsluiting en de lopende rente rekenen hiermee. */
    static final LocalDate GEOPEND_OP = EERSTE_MAAND.atDay(1);
    private static final YearMonth LAATSTE_MAAND = YearMonth.of(2026, 12);
    private static final Bedrag GEEN_BEDRAG = Bedrag.van("0.00");
    private static final BigDecimal HONDERD = BigDecimal.valueOf(100);
    private static final BigDecimal VAKANTIEGELD_DEEL = new BigDecimal("0.92");
    private static final long SEED_INLEG = 43;
    /** FO: 6–12 keer per maand, € 8–120, aan de betaalautomaat. */
    private static final Aankopen BOODSCHAPPEN = new Aankopen(42, 6, 12, 800, 12_000, 1,
            List.of("BuurtSuper", "Prijsvast Supermarkt", "Versmarkt"), TransactieType.BETAALAUTOMAAT);
    /** FO: 1–4 keer per maand, € 10–250, via iDEAL | Wero. */
    private static final Aankopen WEBWINKELS = new Aankopen(44, 1, 4, 1_000, 25_000, 1,
            List.of("Webwinkel Alles", "BoekenBox", "ModeOnline", "ElektroShop"), TransactieType.IDEAL_WERO);
    /** FO: 0–2 keer per maand, € 20–200, bij een geldautomaat. Origineel: in hele tientjes. */
    private static final Aankopen GELDOPNAMES = new Aankopen(45, 0, 2, 2_000, 20_000, 1_000,
            List.of("Geldautomaat Utrecht Centrum", "Geldautomaat Amersfoort Centrum", "Geldautomaat Zuidstad Centrum"), TransactieType.GELDAUTOMAAT);

    /** Op deze volgorde: een aankoop krijgt wat er na de vorige soort over is, en een nieuwe soort verschuift de bestaande dus niet. */
    private static final List<Aankopen> AANKOPEN = List.of(BOODSCHAPPEN, WEBWINKELS, GELDOPNAMES);
    private static final long SEED_BETALINGEN = 46;

    /** Een soort niet-vaste uitgave, tussen 8:00 en 21:00 (FO). */
    private record Aankopen(long seed, int minPerMaand, int maxPerMaand, int minCenten, int maxCenten, int stapCenten, List<String> tegenpartijen,
            TransactieType type) {
    }

    /** ADR 0042: van de betaalrekening naar de eigen spaarrekening. */
    record Inleg(LocalDateTime tijdstip, Bedrag bedrag) {
    }

    /** ADR 0042: van de spaarrekening naar de eigen betaalrekening. */
    record Opname(LocalDateTime tijdstip, Bedrag bedrag) {
    }

    /** ADR 0044: van de betaalrekening van de ene klant naar die van de andere, met hun gebruikersnamen. */
    record BetalingTussenKlanten(LocalDateTime tijdstip, Bedrag bedrag, String van, String naar) {
    }

    /** ADR 0043: van de renterekening van BankSim naar de spaarrekening, op de laatste dag van de maand. */
    record Rente(YearMonth maand, Bedrag bedrag) {

        LocalDateTime tijdstip() {
            return maand.atEndOfMonth().atTime(23, 59);
        }

        String omschrijving() {
            return "Rente " + maand.getMonth().getDisplayName(TextStyle.FULL, Locale.of("nl"));
        }
    }

    /** ADR 0043, FO: per maand over het eindsaldo van elke dag; de rente telt in de maand erna mee. */
    static List<Rente> rente(DemoKlanten.DemoKlant klant) {
        Map<LocalDate, BigDecimal> mutaties = new TreeMap<>();
        inleggen(klant).forEach(inleg -> mutaties.merge(inleg.tijdstip().toLocalDate(), inleg.bedrag().waarde(), BigDecimal::add));
        opnames(klant).forEach(opname -> mutaties.merge(opname.tijdstip().toLocalDate(), opname.bedrag().waarde().negate(), BigDecimal::add));
        List<Rente> renteBoekingen = new ArrayList<>();
        BigDecimal saldo = klant.beginsaldoSpaar().waarde();
        for (YearMonth maand = EERSTE_MAAND; !maand.isAfter(LAATSTE_MAAND); maand = maand.plusMonths(1)) {
            List<DagSaldo> dagen = new ArrayList<>();
            for (LocalDate dag = maand.atDay(1); !dag.isAfter(maand.atEndOfMonth()); dag = dag.plusDays(1)) {
                saldo = saldo.add(mutaties.getOrDefault(dag, BigDecimal.ZERO));
                dagen.add(new DagSaldo(dag, new Bedrag(saldo)));
            }
            Bedrag rente = RenteCalculator.rente(dagen);
            if (rente.waarde().signum() > 0) {
                renteBoekingen.add(new Rente(maand, rente));
                saldo = saldo.add(rente.waarde());
            }
        }
        return renteBoekingen;
    }

    private Generator() {
    }

    /**
     * ADR 0044, FO: 0–3 keer per maand, € 5–150, via online bankieren, aan een andere klant. Alleen wat de betaler zelf heeft, telt:
     * ontvangen geld maakt een saldo alleen hoger, dus de rekening blijft nooit rood. Na de andere boekingen, zodat die niet verschuiven.
     */
    static List<BetalingTussenKlanten> betalingenTussenKlanten(List<DemoKlanten.DemoKlant> klanten) {
        List<BetalingTussenKlanten> betalingen = new ArrayList<>();
        for (DemoKlanten.DemoKlant van : klanten) {
            List<DemoKlanten.DemoKlant> anderen = klanten.stream().filter(klant -> !klant.equals(van)).toList();
            var soort = new Aankopen(SEED_BETALINGEN, 0, 3, 500, 15_000, 1,
                    anderen.stream().map(DemoKlanten.DemoKlant::naam).toList(), TransactieType.ONLINE_BANKIEREN);
            for (Boeking betaling : aankopen(van, volledigVerloop(van), soort)) {
                String naar = anderen.stream().filter(klant -> klant.naam().equals(betaling.tegenpartij())).findFirst().orElseThrow().gebruikersnaam();
                betalingen.add(new BetalingTussenKlanten(betaling.tijdstip(), new Bedrag(betaling.bedrag().waarde().negate()), van.gebruikersnaam(), naar));
            }
        }
        return betalingen;
    }

    /** Alles wat er op de betaalrekening staat, behalve de betalingen tussen klanten. */
    private static List<Boeking> volledigVerloop(DemoKlanten.DemoKlant klant) {
        List<Boeking> verloop = gecontroleerdVerloop(klant);
        AANKOPEN.forEach(soort -> aankopen(klant, verloop, soort));
        return verloop;
    }

    /** Origineel: elke maand op de 25e, tussen 9:00 en 11:00, dus na het inkomen. */
    static List<Inleg> inleggen(DemoKlanten.DemoKlant klant) {
        List<Inleg> inleggen = new ArrayList<>();
        Random toeval = new Random(SEED_INLEG + klant.gebruikersnaam().hashCode());
        klant.inleg().ifPresent(bedrag -> {
            for (YearMonth maand = EERSTE_MAAND; !maand.isAfter(LAATSTE_MAAND); maand = maand.plusMonths(1)) {
                inleggen.add(new Inleg(maand.atDay(25).atTime(LocalTime.of(9, 0).plusMinutes(toeval.nextInt(2 * 60 + 1))), bedrag));
            }
        });
        return inleggen;
    }

    /**
     * FO, origineel: dreigt een boeking de betaalrekening rood te maken, dan komt er een minuut eerder geld van de spaarrekening.
     * Is daar te weinig, dan volgt er geen opname en faalt de controle op nooit rood.
     */
    static List<Opname> opnames(DemoKlanten.DemoKlant klant) {
        List<Opname> opnames = new ArrayList<>();
        BigDecimal betaal = klant.beginsaldo().waarde();
        BigDecimal spaar = klant.beginsaldoSpaar().waarde();
        for (Boeking boeking : vasteBoekingenEnInleggen(klant)) {
            BigDecimal saldoNa = betaal.add(boeking.bedrag().waarde());
            if (saldoNa.signum() < 0) {
                BigDecimal opname = rondeOpname(saldoNa.negate());
                if (spaar.compareTo(opname) >= 0) {
                    opnames.add(new Opname(boeking.tijdstip().minusMinutes(1), new Bedrag(opname)));
                    saldoNa = saldoNa.add(opname);
                    spaar = spaar.subtract(opname);
                }
            }
            betaal = saldoNa;
            if (boeking.tegenpartij().equals(klant.naam())) {
                spaar = spaar.subtract(boeking.bedrag().waarde());
            }
        }
        return opnames;
    }

    /** Origineel: het tekort naar boven afgerond op honderdtallen, plus 100. */
    private static BigDecimal rondeOpname(BigDecimal tekort) {
        return tekort.divide(HONDERD, 0, RoundingMode.CEILING).add(BigDecimal.ONE).multiply(HONDERD).setScale(2);
    }

    /** Op tijdsvolgorde. De inleg staat er als afschrijving bij, met de klant zelf als tegenpartij. */
    private static List<Boeking> vasteBoekingenEnInleggen(DemoKlanten.DemoKlant klant) {
        List<Boeking> boekingen = vasteBoekingen(klant);
        inleggen(klant).forEach(inleg -> boekingen.add(new Boeking(inleg.tijdstip(), new Bedrag(inleg.bedrag().waarde().negate()),
                klant.naam(), TransactieType.OVERSCHRIJVING, Optional.empty())));
        boekingen.sort(Comparator.comparing(Boeking::tijdstip));
        return boekingen;
    }

    /** De eenzijdige boekingen. De inleg en de opname staan er niet bij, maar tellen wel mee voor nooit rood (ADR 0042). */
    static List<Boeking> boekingen(DemoKlanten.DemoKlant klant) {
        List<Boeking> eenzijdig = vasteBoekingen(klant);
        List<Boeking> verloop = gecontroleerdVerloop(klant);
        AANKOPEN.forEach(soort -> eenzijdig.addAll(aankopen(klant, verloop, soort)));
        eenzijdig.sort(Comparator.comparing(Boeking::tijdstip));
        return eenzijdig;
    }

    /** De vaste boekingen, de inleg en de opnames; een profiel dat hiermee rood zou gaan, laat de K8s Job falen. */
    private static List<Boeking> gecontroleerdVerloop(DemoKlanten.DemoKlant klant) {
        List<Boeking> verloop = vasteBoekingenEnInleggen(klant);
        opnames(klant).forEach(opname -> verloop.add(new Boeking(opname.tijdstip(), opname.bedrag(), klant.naam(),
                TransactieType.OVERSCHRIJVING, Optional.empty())));
        controleerNooitRood(klant, verloop);
        return verloop;
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
            if (klant.werknemer() && maand.getMonth() == Month.MAY) {
                boekingen.add(new Boeking(maand.atDay(24).atTime(LocalTime.of(7, 0)), vakantiegeld(klant),
                        klant.inkomen().tegenpartij(), TransactieType.VERZAMELBETALING, Optional.empty()));
            }
            for (DemoKlanten.VasteLast last : klant.vasteLasten()) {
                boekingen.add(new Boeking(maand.atDay(last.dag()).atTime(LocalTime.of(6, 0)),
                        new Bedrag(last.bedrag().waarde().negate()), last.tegenpartij(), TransactieType.INCASSO, Optional.empty()));
            }
        }
        return boekingen;
    }

    /** Origineel: 92% van het inkomen. */
    private static Bedrag vakantiegeld(DemoKlanten.DemoKlant klant) {
        return new Bedrag(klant.inkomen().bedrag().waarde().multiply(VAKANTIEGELD_DEEL).setScale(2, RoundingMode.HALF_UP));
    }

    /** Vaste seed per klant: dezelfde klant krijgt altijd dezelfde aankopen. Een aankoop die de rekening rood zou maken, vervalt (FO). */
    private static List<Boeking> aankopen(DemoKlanten.DemoKlant klant, List<Boeking> verloop, Aankopen soort) {
        List<Boeking> aankopen = new ArrayList<>();
        Random toeval = new Random(soort.seed() + klant.gebruikersnaam().hashCode());
        for (YearMonth maand = EERSTE_MAAND; !maand.isAfter(LAATSTE_MAAND); maand = maand.plusMonths(1)) {
            int aantal = soort.minPerMaand() + toeval.nextInt(soort.maxPerMaand() - soort.minPerMaand() + 1);
            for (int i = 0; i < aantal; i++) {
                Boeking aankoop = aankoop(maand, toeval, soort);
                if (past(klant, verloop, aankoop)) {
                    verloop.add(aankoop);
                    aankopen.add(aankoop);
                }
            }
        }
        return aankopen;
    }

    private static Boeking aankoop(YearMonth maand, Random toeval, Aankopen soort) {
        LocalDateTime tijdstip = maand.atDay(1 + toeval.nextInt(maand.lengthOfMonth()))
                .atTime(LocalTime.of(8, 0).plusMinutes(toeval.nextInt(13 * 60 + 1)));
        int stappen = (soort.maxCenten() - soort.minCenten()) / soort.stapCenten() + 1;
        BigDecimal bedrag = BigDecimal.valueOf(soort.minCenten() + toeval.nextInt(stappen) * soort.stapCenten(), 2).negate();
        String tegenpartij = soort.tegenpartijen().get(toeval.nextInt(soort.tegenpartijen().size()));
        return new Boeking(tijdstip, new Bedrag(bedrag), tegenpartij, soort.type(), Optional.empty());
    }

    /** FO, ADR 0025: dezelfde regel als bij een betaling in de bank, dus inclusief de latere boekingen. */
    private static boolean past(DemoKlanten.DemoKlant klant, List<Boeking> boekingen, Boeking aankoop) {
        BigDecimal saldo = klant.beginsaldo().waarde();
        Map<LocalDate, BigDecimal> later = new TreeMap<>();
        for (Boeking boeking : boekingen) {
            if (boeking.datum().isAfter(aankoop.datum())) {
                later.merge(boeking.datum(), boeking.bedrag().waarde(), BigDecimal::add);
            } else {
                saldo = saldo.add(boeking.bedrag().waarde());
            }
        }
        var verloop = new SaldoVerloop(new Bedrag(saldo), later.values().stream().map(Bedrag::new).toList());
        return NooitRoodRegel.staatToe(verloop, new Bedrag(aankoop.bedrag().waarde().negate()));
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
