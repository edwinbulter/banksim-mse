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
 * vaste bedragen: de woonlasten op de 1e en de vaste lasten op hun eigen dag, vroeg in de ochtend, met de huur en de vaste lasten maal de inflatie van dat jaar, het inkomen op de 24e, dat elk jaar meestijgt met de lonen, in mei met het vakantiegeld van een werknemer, de belastingteruggave in het voorjaar, de inleg op de 25e, in mei met de extra inleg van een werknemer op de 27e, en de gemeentelijke belastingen op de 28e, van maart t/m december, ook maal de inflatie. Daarna de boodschappen, de
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
    /** FO: 6–12 keer per maand, € 8–120, aan de betaalautomaat. Origineel: maal de inflatie. */
    private static final Aankopen BOODSCHAPPEN = new Aankopen(42, 6, 12, 800, 12_000, 1,
            List.of("BuurtSuper", "Prijsvast Supermarkt", "Versmarkt"), TransactieType.BETAALAUTOMAAT, true);
    /** FO: 1–4 keer per maand, € 10–250, via iDEAL | Wero. Origineel: maal de inflatie. */
    private static final Aankopen WEBWINKELS = new Aankopen(44, 1, 4, 1_000, 25_000, 1,
            List.of("Webwinkel Alles", "BoekenBox", "ModeOnline", "ElektroShop"), TransactieType.IDEAL_WERO, true);
    /** FO: 0–2 keer per maand, € 20–200, bij een geldautomaat. Origineel: in hele tientjes, zonder inflatie. */
    private static final Aankopen GELDOPNAMES = new Aankopen(45, 0, 2, 2_000, 20_000, 1_000,
            List.of("Geldautomaat Utrecht Centrum", "Geldautomaat Amersfoort Centrum", "Geldautomaat Zuidstad Centrum"), TransactieType.GELDAUTOMAAT, false);

    /** Op deze volgorde: een aankoop krijgt wat er na de vorige soort over is, en een nieuwe soort verschuift de bestaande dus niet. */
    private static final List<Aankopen> AANKOPEN = List.of(BOODSCHAPPEN, WEBWINKELS, GELDOPNAMES);
    private static final long SEED_BETALINGEN = 46;
    private static final long SEED_TERUGGAVE = 47;
    private static final long SEED_EXTRA_INLEG = 48;
    private static final BigDecimal EXTRA_INLEG_DEEL = new BigDecimal("0.40");
    /** Origineel: de lonen en uitkeringen per jaar ten opzichte van 2021. */
    private static final Map<Integer, BigDecimal> LOONSTIJGING = Map.of(2021, new BigDecimal("1.00"), 2022, new BigDecimal("1.04"),
            2023, new BigDecimal("1.12"), 2024, new BigDecimal("1.18"), 2025, new BigDecimal("1.22"), 2026, new BigDecimal("1.26"));
    /** Origineel: het prijsniveau per jaar ten opzichte van 2021. */
    private static final Map<Integer, BigDecimal> INFLATIE = Map.of(2021, new BigDecimal("1.00"), 2022, new BigDecimal("1.10"),
            2023, new BigDecimal("1.16"), 2024, new BigDecimal("1.20"), 2025, new BigDecimal("1.24"), 2026, new BigDecimal("1.28"));
    /** Origineel: de energieprijs per jaar ten opzichte van 2021, met de piek van 2022. */
    private static final Map<Integer, BigDecimal> ENERGIEPRIJS = Map.of(2021, new BigDecimal("1.00"), 2022, new BigDecimal("1.80"),
            2023, new BigDecimal("1.50"), 2024, new BigDecimal("1.25"), 2025, new BigDecimal("1.20"), 2026, new BigDecimal("1.20"));

    /** Een soort niet-vaste uitgave, tussen 8:00 en 21:00 (FO). Met inflatie geldt het bereik voor 2021 en gaat het bedrag daarna mee. */
    private record Aankopen(long seed, int minPerMaand, int maxPerMaand, int minCenten, int maxCenten, int stapCenten, List<String> tegenpartijen,
            TransactieType type, boolean metInflatie) {
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
                    anderen.stream().map(DemoKlanten.DemoKlant::naam).toList(), TransactieType.ONLINE_BANKIEREN, false);
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

    /** De vaste inleg van elke maand, en de extra inleg van een werknemer na zijn vakantiegeld. */
    static List<Inleg> inleggen(DemoKlanten.DemoKlant klant) {
        List<Inleg> inleggen = vasteInleggen(klant);
        if (klant.soort() == DemoKlanten.Soort.WERKNEMER) {
            inleggen.addAll(extraInleggen(klant));
        }
        return inleggen;
    }

    /**
     * Origineel: elke mei op de 27e, tussen 9:00 en 11:00, 40% van het salaris. Zou hij de rekening rood maken, dan vervalt hij,
     * net als een aankoop (FO). Hij vraagt dus nooit een opname: de opnames gaan alleen over de vaste boekingen en de vaste inleg.
     */
    private static List<Inleg> extraInleggen(DemoKlanten.DemoKlant klant) {
        List<Inleg> inleggen = new ArrayList<>();
        List<Boeking> verloop = metInleggen(klant, vasteInleggen(klant));
        opnames(klant).forEach(opname -> verloop.add(alsBoeking(klant, opname)));
        Random toeval = new Random(SEED_EXTRA_INLEG + klant.gebruikersnaam().hashCode());
        for (int jaar = EERSTE_MAAND.getYear() + 1; jaar <= LAATSTE_MAAND.getYear(); jaar++) {
            Bedrag bedrag = deelVan(inkomen(klant, jaar), EXTRA_INLEG_DEEL);
            Inleg inleg = new Inleg(LocalDate.of(jaar, Month.MAY, 27).atTime(LocalTime.of(9, 0).plusMinutes(toeval.nextInt(2 * 60 + 1))), bedrag);
            if (past(klant, verloop, alsBoeking(klant, inleg))) {
                verloop.add(alsBoeking(klant, inleg));
                inleggen.add(inleg);
            }
        }
        return inleggen;
    }

    /** Origineel: elke maand op de 25e, tussen 9:00 en 11:00, dus na het inkomen. */
    private static List<Inleg> vasteInleggen(DemoKlanten.DemoKlant klant) {
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
        for (Boeking boeking : metInleggen(klant, vasteInleggen(klant))) {
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

    /** De vaste boekingen en de inleggen, op tijdsvolgorde. */
    private static List<Boeking> metInleggen(DemoKlanten.DemoKlant klant, List<Inleg> inleggen) {
        List<Boeking> boekingen = vasteBoekingen(klant);
        inleggen.forEach(inleg -> boekingen.add(alsBoeking(klant, inleg)));
        boekingen.sort(Comparator.comparing(Boeking::tijdstip));
        return boekingen;
    }

    /** De inleg staat op de betaalrekening als afschrijving, met de klant zelf als tegenpartij. */
    private static Boeking alsBoeking(DemoKlanten.DemoKlant klant, Inleg inleg) {
        return new Boeking(inleg.tijdstip(), new Bedrag(inleg.bedrag().waarde().negate()), klant.naam(), TransactieType.OVERSCHRIJVING, Optional.empty());
    }

    private static Boeking alsBoeking(DemoKlanten.DemoKlant klant, Opname opname) {
        return new Boeking(opname.tijdstip(), opname.bedrag(), klant.naam(), TransactieType.OVERSCHRIJVING, Optional.empty());
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
        List<Boeking> verloop = metInleggen(klant, inleggen(klant));
        opnames(klant).forEach(opname -> verloop.add(alsBoeking(klant, opname)));
        controleerNooitRood(klant, verloop);
        return verloop;
    }

    private static List<Boeking> vasteBoekingen(DemoKlanten.DemoKlant klant) {
        List<Boeking> boekingen = new ArrayList<>();
        for (YearMonth maand = EERSTE_MAAND; !maand.isAfter(LAATSTE_MAAND); maand = maand.plusMonths(1)) {
            YearMonth deze = maand;
            klant.woonlasten().ifPresent(woonlasten -> boekingen.add(new Boeking(deze.atDay(1).atTime(LocalTime.of(6, 0)),
                    new Bedrag(woonlasten(woonlasten, deze.getYear()).waarde().negate()), woonlasten.tegenpartij(), TransactieType.INCASSO,
                    Optional.empty())));
            boekingen.add(new Boeking(maand.atDay(24).atTime(LocalTime.of(7, 0)), inkomen(klant, maand.getYear()),
                    klant.inkomen().tegenpartij(), TransactieType.OVERSCHRIJVING, Optional.empty()));
            if (klant.soort() == DemoKlanten.Soort.WERKNEMER && maand.getMonth() == Month.MAY) {
                boekingen.add(new Boeking(maand.atDay(24).atTime(LocalTime.of(7, 0)), deelVan(inkomen(klant, maand.getYear()), VAKANTIEGELD_DEEL),
                        klant.inkomen().tegenpartij(), TransactieType.VERZAMELBETALING, Optional.empty()));
            }
            if (maand.getMonth().compareTo(Month.MARCH) >= 0) {
                klant.termijnGemeente().ifPresent(termijn -> boekingen.add(new Boeking(deze.atDay(28).atTime(LocalTime.of(6, 0)),
                        new Bedrag(deelVan(termijn, INFLATIE.get(deze.getYear())).waarde().negate()), "Gemeente Zuidstad", TransactieType.INCASSO,
                        Optional.empty())));
            }
            for (DemoKlanten.VasteLast last : klant.vasteLasten()) {
                boekingen.add(new Boeking(maand.atDay(last.dag()).atTime(LocalTime.of(6, 0)),
                        new Bedrag(bedragVan(last, maand.getYear()).waarde().negate()), last.tegenpartij(), TransactieType.INCASSO, Optional.empty()));
            }
        }
        if (klant.soort() != DemoKlanten.Soort.ZZP) {
            boekingen.addAll(belastingteruggaven(klant));
        }
        return boekingen;
    }

    /** Origineel: één keer per jaar in april, mei of juni, op dag 1 t/m 28, tussen 1:00 en 6:00, € 80–1.200. */
    private static List<Boeking> belastingteruggaven(DemoKlanten.DemoKlant klant) {
        List<Boeking> teruggaven = new ArrayList<>();
        Random toeval = new Random(SEED_TERUGGAVE + klant.gebruikersnaam().hashCode());
        for (int jaar = EERSTE_MAAND.getYear() + 1; jaar <= LAATSTE_MAAND.getYear(); jaar++) {
            LocalDateTime tijdstip = LocalDate.of(jaar, 4 + toeval.nextInt(3), 1 + toeval.nextInt(28))
                    .atTime(LocalTime.of(1, 0).plusMinutes(toeval.nextInt(5 * 60 + 1)));
            BigDecimal bedrag = BigDecimal.valueOf(8_000 + toeval.nextInt(112_001), 2);
            teruggaven.add(new Boeking(tijdstip, new Bedrag(bedrag), "Belastingdienst", TransactieType.OVERSCHRIJVING, Optional.empty()));
        }
        return teruggaven;
    }

    /** Origineel: het inkomen van 2021 maal de loonstijging van het jaar. */
    private static Bedrag inkomen(DemoKlanten.DemoKlant klant, int jaar) {
        return deelVan(klant.inkomen().bedrag(), LOONSTIJGING.get(jaar));
    }

    /** Origineel: de huur stijgt met de inflatie, de hypotheek blijft gelijk. */
    private static Bedrag woonlasten(DemoKlanten.Woonlasten woonlasten, int jaar) {
        return switch (woonlasten.vorm()) {
            case HUUR -> deelVan(woonlasten.bedrag(), INFLATIE.get(jaar));
            case HYPOTHEEK -> woonlasten.bedrag();
        };
    }

    /** Origineel: een vaste last stijgt met de inflatie; de energie volgt de energieprijzen. */
    private static Bedrag bedragVan(DemoKlanten.VasteLast last, int jaar) {
        return switch (last.prijsreeks()) {
            case INFLATIE -> deelVan(last.bedrag(), INFLATIE.get(jaar));
            case ENERGIE -> deelVan(last.bedrag(), ENERGIEPRIJS.get(jaar));
        };
    }

    /** Afgerond op centen, de helft naar boven: het vakantiegeld is 92% van het inkomen, de extra inleg 40%, en de huur in 2022 110%. */
    private static Bedrag deelVan(Bedrag bedrag, BigDecimal factor) {
        return new Bedrag(bedrag.waarde().multiply(factor).setScale(2, RoundingMode.HALF_UP));
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
        Bedrag getrokken = new Bedrag(BigDecimal.valueOf(soort.minCenten() + toeval.nextInt(stappen) * soort.stapCenten(), 2));
        Bedrag bedrag = soort.metInflatie() ? deelVan(getrokken, INFLATIE.get(maand.getYear())) : getrokken;
        String tegenpartij = soort.tegenpartijen().get(toeval.nextInt(soort.tegenpartijen().size()));
        return new Boeking(tijdstip, new Bedrag(bedrag.waarde().negate()), tegenpartij, soort.type(), Optional.empty());
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
