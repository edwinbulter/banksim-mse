package nl.banksim.seed;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.Month;
import java.time.YearMonth;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import nl.banksim.domain.geld.Bedrag;
import nl.banksim.domain.transactie.TransactieType;
import org.junit.jupiter.api.Test;

/** ADR 0041, FO "Transactiepatroon per maand": het inkomen, de woonlasten en de boodschappen, van oktober 2021 t/m december 2026. */
class GeneratorTests {

    private static final int MAANDEN = 63;
    private static final Bedrag GEEN_SPAARGELD = Bedrag.van("0.00");

    private static DemoKlanten.DemoKlant klant(String gebruikersnaam) {
        return DemoKlanten.ALLE.stream().filter(klant -> klant.gebruikersnaam().equals(gebruikersnaam)).findFirst().orElseThrow();
    }

    /** Alleen het inkomen en de woonlasten, dus zonder het vakantiegeld. */
    private static Optional<DemoKlanten.Woonlasten> huur(String bedrag) {
        return Optional.of(new DemoKlanten.Woonlasten(DemoKlanten.Woonvorm.HUUR, "Verhuurder", Bedrag.van(bedrag)));
    }

    private static List<Boeking> vasteBoekingen(DemoKlanten.DemoKlant klant) {
        return Generator.boekingen(klant).stream()
                .filter(boeking -> boeking.type() != TransactieType.VERZAMELBETALING)
                .filter(boeking -> boeking.tegenpartij().equals(klant.inkomen().tegenpartij())
                        || klant.woonlasten().filter(woonlasten -> woonlasten.tegenpartij().equals(boeking.tegenpartij())).isPresent())
                .toList();
    }

    private static List<Boeking> boodschappen(DemoKlanten.DemoKlant klant) {
        return Generator.boekingen(klant).stream().filter(boeking -> boeking.type() == TransactieType.BETAALAUTOMAAT).toList();
    }

    private static List<Boeking> webwinkelaankopen(DemoKlanten.DemoKlant klant) {
        return Generator.boekingen(klant).stream().filter(boeking -> boeking.type() == TransactieType.IDEAL_WERO).toList();
    }

    private static List<Boeking> vakantiegeld(DemoKlanten.DemoKlant klant) {
        return Generator.boekingen(klant).stream().filter(boeking -> boeking.type() == TransactieType.VERZAMELBETALING).toList();
    }

    private static List<Boeking> termijnen(DemoKlanten.DemoKlant klant) {
        return Generator.boekingen(klant).stream().filter(boeking -> boeking.tegenpartij().equals("Gemeente Zuidstad")).toList();
    }

    private static DemoKlanten.DemoKlant metTermijn(String inkomen) {
        return new DemoKlanten.DemoKlant("termijn", "Termijn", Bedrag.van("0.00"), GEEN_SPAARGELD,
                new DemoKlanten.VasteBoeking("Pensioenfonds", Bedrag.van(inkomen)), Optional.empty(), List.of(), Optional.empty(),
                DemoKlanten.Soort.ZZP, Optional.of(Bedrag.van("38.00")));
    }

    private static List<Boeking> teruggaven(DemoKlanten.DemoKlant klant) {
        return Generator.boekingen(klant).stream().filter(boeking -> boeking.tegenpartij().equals("Belastingdienst")).toList();
    }

    /**
     * Zonder eigen ruimte om uit te geven: na de eerste hypotheek blijft er € 12,00 over, en in 2021 is de hypotheek van € 104,00 € 4,00
     * hoger dan het inkomen, wat die € 12,00 in drie maanden opmaakt. In 2022 is het inkomen met de loonstijging precies € 104,00 (ADR 0041, FO).
     */
    private static DemoKlanten.DemoKlant krap(String gebruikersnaam, DemoKlanten.Soort soort) {
        return new DemoKlanten.DemoKlant(gebruikersnaam, gebruikersnaam, Bedrag.van("116.00"), GEEN_SPAARGELD,
                new DemoKlanten.VasteBoeking("Werkgever", Bedrag.van("100.00")), hypotheek("104.00"), List.of(), Optional.empty(), soort, Optional.empty());
    }

    /** Een last die elk jaar gelijk blijft, op de 1e om 6:00: voor regels over geld dat net wel of net niet genoeg is. */
    private static Optional<DemoKlanten.Woonlasten> hypotheek(String bedrag) {
        return Optional.of(new DemoKlanten.Woonlasten(DemoKlanten.Woonvorm.HYPOTHEEK, "Hypotheekbank", Bedrag.van(bedrag)));
    }

    private static List<Boeking> geldopnames(DemoKlanten.DemoKlant klant) {
        return Generator.boekingen(klant).stream().filter(boeking -> boeking.type() == TransactieType.GELDAUTOMAAT).toList();
    }

    private static Boeking incasso(int jaar, int maand, int dag, String bedrag, String aan) {
        return woonlasten(LocalDateTime.of(jaar, maand, dag, 6, 0), bedrag, aan);
    }

    private static Boeking inkomen(LocalDateTime tijdstip, String bedrag, String van) {
        return new Boeking(tijdstip, Bedrag.van(bedrag), van, TransactieType.OVERSCHRIJVING, Optional.empty());
    }

    private static Boeking woonlasten(LocalDateTime tijdstip, String bedrag, String aan) {
        return new Boeking(tijdstip, Bedrag.van(bedrag), aan, TransactieType.INCASSO, Optional.empty());
    }

    @Test
    void deWoonlastenGaanOpDeEersteEnHetInkomenKomtOpDe24e() {
        List<Boeking> boekingen = vasteBoekingen(klant("jdevries"));

        assertThat(boekingen).hasSize(2 * MAANDEN);
        assertThat(boekingen.subList(0, 2)).containsExactly(
                woonlasten(LocalDateTime.of(2021, 10, 1, 6, 0), "-950.00", "Woonstichting Thuis"),
                inkomen(LocalDateTime.of(2021, 10, 24, 7, 0), "3100.00", "Techniek Noord B.V."));
        assertThat(boekingen.subList(boekingen.size() - 2, boekingen.size())).containsExactly(
                woonlasten(LocalDateTime.of(2026, 12, 1, 6, 0), "-1216.00", "Woonstichting Thuis"),
                inkomen(LocalDateTime.of(2026, 12, 24, 7, 0), "3906.00", "Techniek Noord B.V."));
    }

    /** Origineel: de lonen en uitkeringen per jaar ten opzichte van 2021, dus 1,00 · 1,04 · 1,12 · 1,18 · 1,22 · 1,26. */
    @Test
    void hetInkomenStijgtElkJaarMetDeLonen() {
        Map<Integer, Bedrag> jan = vasteBoekingen(klant("jdevries")).stream()
                .filter(boeking -> boeking.tegenpartij().equals("Techniek Noord B.V."))
                .collect(Collectors.toMap(boeking -> boeking.tijdstip().getYear(), Boeking::bedrag, (eerste, tweede) -> {
                    assertThat(tweede).isEqualTo(eerste);
                    return eerste;
                }));

        assertThat(jan).isEqualTo(Map.of(2021, Bedrag.van("3100.00"), 2022, Bedrag.van("3224.00"), 2023, Bedrag.van("3472.00"),
                2024, Bedrag.van("3658.00"), 2025, Bedrag.van("3782.00"), 2026, Bedrag.van("3906.00")));
        assertThat(vasteBoekingen(klant("pvisser")).getLast().bedrag()).isEqualTo(Bedrag.van("3087.00"));
    }

    /** € 100,13 × 1,04 = € 104,1352: afgerond op centen, de helft naar boven, net als het vakantiegeld. */
    @Test
    void eenKromInkomenMetLoonstijgingWordtAfgerondOpCenten() {
        var krom = new DemoKlanten.DemoKlant("krom", "Krom", Bedrag.van("0.00"), GEEN_SPAARGELD,
                new DemoKlanten.VasteBoeking("Werkgever", Bedrag.van("100.13")), Optional.empty(), List.of(), Optional.empty());

        assertThat(vasteBoekingen(krom)).filteredOn(boeking -> boeking.tijdstip().getYear() == 2022)
                .extracting(Boeking::bedrag).containsOnly(Bedrag.van("104.14"));
    }

    /** Origineel: het prijsniveau per jaar ten opzichte van 2021, dus 1,00 · 1,10 · 1,16 · 1,20 · 1,24 · 1,28. */
    @Test
    void deHuurStijgtElkJaarMetDeInflatie() {
        assertThat(bedragPerJaar(klant("jdevries"), "Woonstichting Thuis")).isEqualTo(Map.of(2021, Bedrag.van("-950.00"), 2022, Bedrag.van("-1045.00"),
                2023, Bedrag.van("-1102.00"), 2024, Bedrag.van("-1140.00"), 2025, Bedrag.van("-1178.00"), 2026, Bedrag.van("-1216.00")));
    }

    @Test
    void deHypotheekBlijftGelijk() {
        assertThat(Generator.boekingen(klant("sbakker"))).filteredOn(boeking -> boeking.tegenpartij().equals("Hypotheekbank Hollandia"))
                .hasSize(MAANDEN).extracting(Boeking::bedrag).containsOnly(Bedrag.van("-1350.00"));
    }

    /** € 100,15 × 1,10 = € 110,165: afgerond op centen, de helft naar boven, net als de loonstijging. */
    @Test
    void eenKromeHuurEnVasteLastMetInflatieWordenAfgerondOpCenten() {
        var krom = new DemoKlanten.DemoKlant("krom", "Krom", Bedrag.van("300.00"), GEEN_SPAARGELD,
                new DemoKlanten.VasteBoeking("Werkgever", Bedrag.van("3100.00")), huur("100.15"),
                List.of(new DemoKlanten.VasteLast(20, "Internet", Bedrag.van("100.15"))), Optional.empty());

        assertThat(Generator.boekingen(krom)).filteredOn(boeking -> boeking.tijdstip().getYear() == 2022)
                .filteredOn(boeking -> boeking.type() == TransactieType.INCASSO)
                .extracting(Boeking::bedrag).containsOnly(Bedrag.van("-110.17"));
    }

    /** Per jaar het ene bedrag aan de tegenpartij; een tweede, ander bedrag in hetzelfde jaar laat de test falen. */
    private static Map<Integer, Bedrag> bedragPerJaar(DemoKlanten.DemoKlant klant, String tegenpartij) {
        return Generator.boekingen(klant).stream()
                .filter(boeking -> boeking.tegenpartij().equals(tegenpartij))
                .collect(Collectors.toMap(boeking -> boeking.tijdstip().getYear(), Boeking::bedrag, (eerste, tweede) -> {
                    assertThat(tweede).isEqualTo(eerste);
                    return eerste;
                }));
    }

    @Test
    void zonderWoonlastenBlijftAlleenHetInkomen() {
        List<Boeking> boekingen = vasteBoekingen(klant("pvisser"));

        assertThat(boekingen).hasSize(MAANDEN);
        assertThat(boekingen.getFirst()).isEqualTo(inkomen(LocalDateTime.of(2021, 10, 24, 7, 0), "2450.00", "Pensioenfonds Midden"));
    }

    @Test
    void elkeKlantHeeftInElkeMaandEenBoeking() {
        for (DemoKlanten.DemoKlant klant : DemoKlanten.ALLE) {
            assertThat(Generator.boekingen(klant).stream().map(boeking -> YearMonth.from(boeking.tijdstip())).distinct())
                    .as(klant.gebruikersnaam())
                    .hasSize(MAANDEN)
                    .startsWith(YearMonth.of(2021, 10))
                    .endsWith(YearMonth.of(2026, 12));
        }
    }

    @Test
    void deBoekingenStaanOpTijdsvolgorde() {
        for (DemoKlanten.DemoKlant klant : DemoKlanten.ALLE) {
            assertThat(Generator.boekingen(klant)).as(klant.gebruikersnaam()).isSortedAccordingTo(Comparator.comparing(Boeking::tijdstip));
        }
    }

    /** ADR 0041: op elke installatiedatum dezelfde data. */
    @Test
    void tweeKeerGenererenGeeftDezelfdeBoekingen() {
        assertThat(Generator.boekingen(klant("sbakker"))).isNotEmpty().isEqualTo(Generator.boekingen(klant("sbakker")));
    }

    /** S11.1, FO: dezelfde regel als in de bank; een profiel dat rood zou gaan, wordt geen demo-data. */
    @Test
    void eenKlantDieRoodZouGaanGeeftEenFout() {
        var teWeinig = new DemoKlanten.DemoKlant("rood", "Rood", Bedrag.van("949.99"), GEEN_SPAARGELD,
                new DemoKlanten.VasteBoeking("Werkgever", Bedrag.van("3100.00")),
                huur("950.00"), List.of(), Optional.empty());

        assertThatThrownBy(() -> Generator.boekingen(teWeinig))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("rood");
    }

    /** Precies op € 0,00 is niet rood. */
    @Test
    void eenKlantDiePreciesOpNulKomtMag() {
        var precies = new DemoKlanten.DemoKlant("nul", "Nul", Bedrag.van("950.00"), GEEN_SPAARGELD,
                new DemoKlanten.VasteBoeking("Werkgever", Bedrag.van("3100.00")),
                huur("950.00"), List.of(), Optional.empty());

        assertThat(vasteBoekingen(precies)).hasSize(2 * MAANDEN);
    }

    /** Elke dag telt: pas later in de maand komt het inkomen binnen. */
    @Test
    void eenDipTussenTweeBoekingenTeltOokAlsHetEindsaldoPositiefIs() {
        var dip = new DemoKlanten.DemoKlant("dip", "Dip", Bedrag.van("100.00"), GEEN_SPAARGELD,
                new DemoKlanten.VasteBoeking("Werkgever", Bedrag.van("3100.00")),
                huur("950.00"), List.of(), Optional.empty());

        assertThatThrownBy(() -> Generator.boekingen(dip)).isInstanceOf(IllegalStateException.class);
    }

    /** FO: boodschappen 6–12 keer per maand. pvisser heeft geen woonlasten en komt dus nooit in de buurt van een tekort. */
    @Test
    void eenKlantZonderTekortDoetElkeMaand6Tot12KeerBoodschappen() {
        Map<YearMonth, Long> perMaand = boodschappen(klant("pvisser")).stream()
                .collect(Collectors.groupingBy(boeking -> YearMonth.from(boeking.tijdstip()), Collectors.counting()));

        assertThat(perMaand).hasSize(MAANDEN);
        assertThat(perMaand.values()).allSatisfy(aantal -> assertThat(aantal).isBetween(6L, 12L));
        assertThat(perMaand.values()).contains(6L, 12L);
    }

    /** FO: een bedrag van € 8–120 aan een supermarkt, aan de betaalautomaat, tussen 8:00 en 21:00, zonder tegen-IBAN. */
    @Test
    void eenBoodschapHeeftHetBedragDeTegenpartijEnHetTijdstipUitHetFo() {
        for (DemoKlanten.DemoKlant klant : DemoKlanten.ALLE) {
            assertThat(boodschappen(klant)).as(klant.gebruikersnaam()).isNotEmpty().allSatisfy(boeking -> {
                assertThat(boeking.bedrag().waarde()).isBetween(new BigDecimal("-120.00"), new BigDecimal("-8.00"));
                assertThat(boeking.tegenpartij()).isIn("BuurtSuper", "Prijsvast Supermarkt", "Versmarkt");
                assertThat(boeking.tijdstip().toLocalTime()).isBetween(LocalTime.of(8, 0), LocalTime.of(21, 0));
                assertThat(boeking.tegenIban()).isEmpty();
            });
        }
    }

    /** De grenzen worden ook echt gehaald: alle drie de supermarkten, en bedragen en tijden over het hele bereik. */
    @Test
    void deBoodschappenVarierenOverHetHeleBereik() {
        List<Boeking> alle = DemoKlanten.ALLE.stream().flatMap(klant -> boodschappen(klant).stream()).toList();

        assertThat(alle).extracting(Boeking::tegenpartij).contains("BuurtSuper", "Prijsvast Supermarkt", "Versmarkt");
        assertThat(alle).extracting(boeking -> boeking.bedrag().waarde()).contains(new BigDecimal("-8.00"), new BigDecimal("-120.00"));
        assertThat(alle).extracting(boeking -> boeking.tijdstip().toLocalTime()).contains(LocalTime.of(8, 0), LocalTime.of(21, 0));
        assertThat(alle).extracting(boeking -> boeking.tijdstip().getDayOfMonth()).contains(1, 31);
    }

    /** Vaste seed per klant (ADR 0041): een klant erbij verschuift de data van de anderen niet, en klanten verschillen. */
    @Test
    void elkeKlantHeeftEigenBoodschappen() {
        assertThat(boodschappen(klant("pvisser"))).isNotEqualTo(boodschappen(klant("rmulder")));
    }

    /** FO: bij een dreigend tekort wordt een niet-vaste uitgave overgeslagen; tot het salaris op de 24e staat er € 0,00. */
    @Test
    void bijEenDreigendTekortWordtEenBoodschapOvergeslagen() {
        var krap = new DemoKlanten.DemoKlant("krap", "Krap", Bedrag.van("950.00"), GEEN_SPAARGELD,
                new DemoKlanten.VasteBoeking("Werkgever", Bedrag.van("3100.00")),
                huur("950.00"), List.of(), Optional.empty());

        assertThat(boodschappen(krap))
                .isNotEmpty()
                .filteredOn(boeking -> boeking.tijdstip().isBefore(LocalDateTime.of(2021, 10, 24, 0, 0)))
                .isEmpty();
    }

    /** Ook een latere vaste boeking telt mee: geen boodschap mag de huur van 1 november rood maken. */
    @Test
    void eenBoodschapMagEenLatereBoekingNietRoodMaken() {
        var krap = new DemoKlanten.DemoKlant("krap", "Krap", Bedrag.van("1000.00"), GEEN_SPAARGELD,
                new DemoKlanten.VasteBoeking("Werkgever", Bedrag.van("100.00")),
                huur("100.00"), List.of(), Optional.empty());

        BigDecimal lopend = krap.beginsaldo().waarde();
        Map<LocalDate, BigDecimal> perDag = new TreeMap<>();
        Generator.boekingen(krap).forEach(boeking -> perDag.merge(boeking.datum(), boeking.bedrag().waarde(), BigDecimal::add));
        for (BigDecimal beweging : perDag.values()) {
            lopend = lopend.add(beweging);
            assertThat(lopend).isGreaterThanOrEqualTo(BigDecimal.ZERO);
        }
        assertThat(boodschappen(krap)).isNotEmpty();
    }

    /** FO, origineel: vroeg in de ochtend een incasso, per last op de eigen dag; in 2026 maal de inflatie van 1,28, behalve de energie. */
    @Test
    void deVasteLastenVanJanInDecember2026() {
        assertThat(Generator.boekingen(klant("jdevries"))).containsAll(List.of(
                incasso(2026, 12, 1, "-179.20", "Zorgverzekeraar Gezond"),
                incasso(2026, 12, 1, "-23.04", "Verzekeringen Zeker"),
                incasso(2026, 12, 15, "-95.00", "Groenstroom Energie"),
                incasso(2026, 12, 20, "-57.60", "NetPlus Internet"),
                incasso(2026, 12, 20, "-9.60", "Waterbedrijf Midden"),
                incasso(2026, 12, 22, "-22.40", "Mobiel Direct")));
    }

    /** Per volwassene de zorgpremie en het mobiel, per persoon het water, en met een auto een duurdere verzekering; in 2021 zonder inflatie. */
    @Test
    void deVasteLastenHangenAfVanHetHuishouden() {
        assertThat(Generator.boekingen(klant("melamrani"))).containsAll(List.of(
                incasso(2021, 12, 1, "-280.00", "Zorgverzekeraar Gezond"),
                incasso(2021, 12, 1, "-73.00", "Verzekeringen Zeker"),
                incasso(2021, 12, 15, "-175.00", "Groenstroom Energie"),
                incasso(2021, 12, 20, "-45.00", "NetPlus Internet"),
                incasso(2021, 12, 20, "-37.50", "Waterbedrijf Midden"),
                incasso(2021, 12, 22, "-35.00", "Mobiel Direct")));
    }

    @Test
    void deVasteLastenStijgenElkJaarMetDeInflatieBehalveDeEnergie() {
        assertThat(bedragPerJaar(klant("melamrani"), "Zorgverzekeraar Gezond")).isEqualTo(Map.of(2021, Bedrag.van("-280.00"), 2022, Bedrag.van("-308.00"),
                2023, Bedrag.van("-324.80"), 2024, Bedrag.van("-336.00"), 2025, Bedrag.van("-347.20"), 2026, Bedrag.van("-358.40")));
        assertThat(bedragPerJaar(klant("melamrani"), "Groenstroom Energie")).containsOnlyKeys(2021, 2022, 2023, 2024, 2025, 2026)
                .allSatisfy((jaar, bedrag) -> assertThat(bedrag).isEqualTo(Bedrag.van("-175.00")));
    }

    @Test
    void elkeKlantBetaaltElkeMaandElkeVasteLastPreciesEenKeer() {
        for (DemoKlanten.DemoKlant klant : DemoKlanten.ALLE) {
            assertThat(klant.vasteLasten()).as(klant.gebruikersnaam()).hasSize(6);
            for (DemoKlanten.VasteLast last : klant.vasteLasten()) {
                List<Boeking> betalingen = Generator.boekingen(klant).stream()
                        .filter(boeking -> boeking.tegenpartij().equals(last.tegenpartij())).toList();
                assertThat(betalingen).as(last.tegenpartij()).hasSize(MAANDEN)
                        .allSatisfy(boeking -> assertThat(boeking.type()).isEqualTo(TransactieType.INCASSO));
            }
        }
    }

    /** Een vaste last vervalt niet (FO): zou hij de rekening rood maken, dan faalt de generator. */
    @Test
    void eenVasteLastDieRoodZouMakenGeeftEenFout() {
        var teWeinig = new DemoKlanten.DemoKlant("rood", "Rood", Bedrag.van("100.00"), GEEN_SPAARGELD,
                new DemoKlanten.VasteBoeking("Werkgever", Bedrag.van("3100.00")), Optional.empty(),
                List.of(new DemoKlanten.VasteLast(1, "Energie", Bedrag.van("100.01"))), Optional.empty());

        assertThatThrownBy(() -> Generator.boekingen(teWeinig)).isInstanceOf(IllegalStateException.class);
    }

    /** Een boodschap houdt rekening met een vaste last later in de maand. */
    @Test
    void eenBoodschapMagEenLatereVasteLastNietRoodMaken() {
        var krap = new DemoKlanten.DemoKlant("krap", "Krap", Bedrag.van("100.00"), GEEN_SPAARGELD,
                new DemoKlanten.VasteBoeking("Werkgever", Bedrag.van("3100.00")), Optional.empty(),
                List.of(new DemoKlanten.VasteLast(23, "Energie", Bedrag.van("100.00"))), Optional.empty());

        assertThat(boodschappen(krap)).isNotEmpty()
                .filteredOn(boeking -> boeking.tijdstip().isBefore(LocalDateTime.of(2021, 10, 24, 0, 0)))
                .isEmpty();
    }

    private static List<Generator.Inleg> vasteInleggen(DemoKlanten.DemoKlant klant) {
        return Generator.inleggen(klant).stream().filter(inleg -> inleg.tijdstip().getDayOfMonth() == 25).toList();
    }

    /** Origineel: elke maand op de 25e, tussen 9:00 en 11:00, een vast bedrag per klant. */
    @Test
    void elkeKlantLegtElkeMaandZijnVasteBedragIn() {
        Map<String, String> bedragen = Map.of("jdevries", "400.00", "sbakker", "600.00", "melamrani", "250.00",
                "ljansen", "150.00", "pvisser", "200.00", "fyilmaz", "500.00", "dsmit", "500.00", "edeboer", "450.00",
                "rmulder", "150.00", "nhendriks", "800.00");

        for (DemoKlanten.DemoKlant klant : DemoKlanten.ALLE) {
            List<Generator.Inleg> inleggen = vasteInleggen(klant);

            assertThat(inleggen).as(klant.gebruikersnaam()).hasSize(MAANDEN).allSatisfy(inleg -> {
                assertThat(inleg.bedrag()).isEqualTo(Bedrag.van(bedragen.get(klant.gebruikersnaam())));
                assertThat(inleg.tijdstip().toLocalTime()).isBetween(LocalTime.of(9, 0), LocalTime.of(11, 0));
            });
            assertThat(YearMonth.from(inleggen.getFirst().tijdstip())).isEqualTo(YearMonth.of(2021, 10));
            assertThat(YearMonth.from(inleggen.getLast().tijdstip())).isEqualTo(YearMonth.of(2026, 12));
        }
    }

    @Test
    void deTijdenVanDeInleggenVarierenEnZijnElkeKeerHetzelfde() {
        List<Generator.Inleg> inleggen = Generator.inleggen(klant("jdevries"));

        assertThat(inleggen).isEqualTo(Generator.inleggen(klant("jdevries")));
        assertThat(inleggen.stream().map(inleg -> inleg.tijdstip().toLocalTime()).distinct().count()).isGreaterThan(1);
    }

    /** ADR 0041: de vaste seed bepaalt het tijdstip, dus elke installatie geeft Jan hetzelfde. */
    @Test
    void deEersteInlegVanJanLigtVastDoorDeSeed() {
        assertThat(Generator.inleggen(klant("jdevries")).getFirst().tijdstip()).isEqualTo(LocalDateTime.of(2021, 10, 25, 10, 44));
    }

    /** ADR 0042: de inleg is een overboeking, geen eenzijdige boeking. */
    @Test
    void deInlegStaatNietBijDeEenzijdigeBoekingen() {
        assertThat(Generator.boekingen(klant("jdevries"))).extracting(Boeking::tegenpartij).doesNotContain("Jan de Vries");
    }

    private static DemoKlanten.DemoKlant spaarder(String inleg) {
        return new DemoKlanten.DemoKlant("spaarder", "Spaarder", Bedrag.van("0.00"), GEEN_SPAARGELD,
                new DemoKlanten.VasteBoeking("Werkgever", Bedrag.van("100.00")), Optional.empty(), List.of(),
                Optional.of(Bedrag.van(inleg)));
    }

    /** ADR 0042: de inleg telt mee als vaste boeking. */
    @Test
    void eenInlegDieRoodZouMakenGeeftEenFout() {
        assertThatThrownBy(() -> Generator.boekingen(spaarder("100.01"))).isInstanceOf(IllegalStateException.class);
    }

    /** In 2021 gaat het salaris van de 24e de 25e helemaal naar de spaarrekening, dus er is niets over voor boodschappen. */
    @Test
    void eenBoodschapMagEenLatereInlegNietRoodMaken() {
        assertThat(boodschappen(spaarder("100.00"))).filteredOn(boodschap -> boodschap.tijdstip().getYear() == 2021).isEmpty();
    }

    @Test
    void zonderInlegZijnErGeenInleggen() {
        var geen = new DemoKlanten.DemoKlant("geen", "Geen", Bedrag.van("0.00"), GEEN_SPAARGELD,
                new DemoKlanten.VasteBoeking("Werkgever", Bedrag.van("100.00")), Optional.empty(), List.of(), Optional.empty());

        assertThat(Generator.inleggen(geen)).isEmpty();
    }

    /** Origineel: de beginsaldi van de betaalrekening en de spaarrekening. */
    @Test
    void deBeginsaldiKomenUitHetOrigineel() {
        Map<String, List<String>> beginsaldi = Map.of("jdevries", List.of("1800.00", "6500.00"),
                "sbakker", List.of("2500.00", "14000.00"), "melamrani", List.of("1500.00", "4500.00"),
                "ljansen", List.of("900.00", "1800.00"), "pvisser", List.of("3000.00", "38000.00"),
                "fyilmaz", List.of("2200.00", "16000.00"), "dsmit", List.of("4000.00", "12000.00"),
                "edeboer", List.of("1700.00", "9000.00"), "rmulder", List.of("2000.00", "22000.00"),
                "nhendriks", List.of("3500.00", "25000.00"));

        assertThat(DemoKlanten.ALLE).hasSize(10).allSatisfy(klant -> assertThat(List.of(klant.beginsaldo(), klant.beginsaldoSpaar()))
                .as(klant.gebruikersnaam())
                .containsExactly(Bedrag.van(beginsaldi.get(klant.gebruikersnaam()).get(0)),
                        Bedrag.van(beginsaldi.get(klant.gebruikersnaam()).get(1))));
    }

    private static DemoKlanten.DemoKlant metTekort(String beginsaldo, String spaargeld, String last) {
        return new DemoKlanten.DemoKlant("tekort", "Tekort", Bedrag.van(beginsaldo), Bedrag.van(spaargeld),
                new DemoKlanten.VasteBoeking("Werkgever", Bedrag.van(last)), hypotheek(last), List.of(), Optional.empty());
    }

    private static Generator.Opname opname(int maand, int dag, String bedrag) {
        return new Generator.Opname(LocalDateTime.of(2021, maand, dag, 5, 59), Bedrag.van(bedrag));
    }

    /** FO: bij een dreigend tekort eerst geld van de spaarrekening, een minuut voor de vaste last. */
    @Test
    void lisaEnMohamedNemenInOktober2021GeldOpVanHunSpaarrekening() {
        assertThat(Generator.opnames(klant("ljansen"))).containsExactly(opname(10, 1, "200.00"));
        assertThat(Generator.opnames(klant("melamrani"))).containsExactly(opname(10, 1, "200.00"), opname(10, 15, "200.00"));
    }

    @Test
    void deAndereKlantenNemenNietsOp() {
        assertThat(DemoKlanten.ALLE).filteredOn(klant -> !List.of("ljansen", "melamrani").contains(klant.gebruikersnaam()))
                .hasSize(8).allSatisfy(klant -> assertThat(Generator.opnames(klant)).as(klant.gebruikersnaam()).isEmpty());
    }

    /** Origineel: het tekort naar boven afgerond op honderdtallen, plus 100. */
    @Test
    void deOpnameIsHetTekortAfgerondOpHonderdtallenPlusHonderd() {
        assertThat(Generator.opnames(metTekort("100.00", "1000.00", "100.01")).getFirst()).isEqualTo(opname(10, 1, "200.00"));
        assertThat(Generator.opnames(metTekort("100.00", "1000.00", "200.00")).getFirst()).isEqualTo(opname(10, 1, "200.00"));
        assertThat(Generator.opnames(metTekort("100.00", "1000.00", "200.01")).getFirst()).isEqualTo(opname(10, 1, "300.00"));
    }

    @Test
    void eenVasteLastDiePreciesOpNulUitkomtVraagtGeenOpname() {
        assertThat(Generator.opnames(metTekort("100.00", "1000.00", "100.00"))).isEmpty();
    }

    /** Het inkomen en de last zijn even groot, dus de opname van € 200,00 blijft het hele verloop dekken. */
    @Test
    void preciesGenoegSpaargeldIsGenoeg() {
        assertThat(Generator.boekingen(metTekort("100.00", "200.00", "100.01"))).isNotEmpty();
        assertThat(Generator.opnames(metTekort("100.00", "200.00", "100.01"))).containsExactly(opname(10, 1, "200.00"));
    }

    /** Nooit rood blijft de laatste controle: zonder genoeg spaargeld wordt het profiel geen demo-data. */
    @Test
    void teWeinigSpaargeldGeeftEenFout() {
        assertThatThrownBy(() -> Generator.boekingen(metTekort("100.00", "199.99", "100.01")))
                .isInstanceOf(IllegalStateException.class);
    }

    /** Elke maand € 100,00 minder: de eerste € 300,00 raakt op, waarna de tweede opname het spaargeld te boven gaat. */
    @Test
    void eenOpnameHaaltHetGeldVanDeSpaarrekening() {
        var krimpend = new DemoKlanten.DemoKlant("krimpend", "Krimpend", Bedrag.van("0.00"), Bedrag.van("399.99"),
                new DemoKlanten.VasteBoeking("Werkgever", Bedrag.van("100.00")), Optional.empty(),
                List.of(new DemoKlanten.VasteLast(1, "Energie", Bedrag.van("200.00"))), Optional.empty());

        assertThatThrownBy(() -> Generator.boekingen(krimpend)).isInstanceOf(IllegalStateException.class);
    }

    /** De spaarrekening begint leeg en heeft op 1 december twee inleggen van € 100,00: precies de opname. */
    @Test
    void deInleggenTellenMeeVoorHetSpaargeld() {
        var spaarder = new DemoKlanten.DemoKlant("spaarder", "Spaarder", Bedrag.van("300.00"), GEEN_SPAARGELD,
                new DemoKlanten.VasteBoeking("Werkgever", Bedrag.van("200.00")), Optional.empty(),
                List.of(new DemoKlanten.VasteLast(1, "Energie", Bedrag.van("200.00"))), Optional.of(Bedrag.van("100.00")));

        assertThat(Generator.opnames(spaarder).getFirst()).isEqualTo(opname(12, 1, "200.00"));
    }

    /** ADR 0042: de opname is een overboeking, maar telt wel mee voor nooit rood en voor de boodschappen. */
    @Test
    void deOpnameStaatNietBijDeEenzijdigeBoekingenMaarTeltWelMee() {
        assertThat(Generator.boekingen(klant("ljansen"))).extracting(Boeking::tegenpartij).doesNotContain("Lisa Jansen");
        assertThat(boodschappen(klant("ljansen"))).isNotEmpty();
    }

    private static List<Generator.Rente> rente(String gebruikersnaam) {
        return Generator.rente(klant(gebruikersnaam));
    }

    /** ADR 0043, FO: 3% per jaar, per dag over het eindsaldo: € 6.500,00, en de inleg van € 400,00 vanaf de 25e. */
    @Test
    void deRenteVanJanInOktober2021() {
        Generator.Rente oktober = rente("jdevries").getFirst();

        assertThat(oktober.maand()).isEqualTo(YearMonth.of(2021, 10));
        assertThat(oktober.bedrag()).isEqualTo(Bedrag.van("16.79"));
        assertThat(oktober.tijdstip()).isEqualTo(LocalDateTime.of(2021, 10, 31, 23, 59));
        assertThat(oktober.omschrijving()).isEqualTo("Rente oktober");
    }

    /** Een opname telt vanaf de dag zelf mee: Lisa en Mohamed nemen in oktober 2021 op. */
    @Test
    void deOpnamesTellenMeeVoorDeRente() {
        assertThat(rente("ljansen").getFirst().bedrag()).isEqualTo(Bedrag.van("4.16"));
        assertThat(rente("melamrani").getFirst().bedrag()).isEqualTo(Bedrag.van("10.82"));
    }

    @Test
    void elkeKlantKrijgtElkeMaandRente() {
        assertThat(DemoKlanten.ALLE).allSatisfy(klant -> {
            List<Generator.Rente> rente = Generator.rente(klant);

            assertThat(rente).as(klant.gebruikersnaam()).hasSize(MAANDEN);
            assertThat(rente.getFirst().maand()).isEqualTo(YearMonth.of(2021, 10));
            assertThat(rente.getLast().maand()).isEqualTo(YearMonth.of(2026, 12));
            assertThat(rente.getLast().omschrijving()).isEqualTo("Rente december");
        });
    }

    /** De bedragen uit het scenario, met een los rekenscript nagerekend: rente telt mee in de maanden erna. */
    @Test
    void hetSpaarsaldoInclusiefRente() {
        for (var verwacht : Map.of("jdevries", "42666.45", "ljansen", "18002.50", "melamrani", "32393.72").entrySet()) {
            var klant = klant(verwacht.getKey());
            BigDecimal saldo = klant.beginsaldoSpaar().waarde();
            for (Generator.Inleg inleg : Generator.inleggen(klant)) {
                saldo = saldo.add(inleg.bedrag().waarde());
            }
            for (Generator.Opname opname : Generator.opnames(klant)) {
                saldo = saldo.subtract(opname.bedrag().waarde());
            }
            for (Generator.Rente rente : Generator.rente(klant)) {
                saldo = saldo.add(rente.bedrag().waarde());
            }

            assertThat(saldo).as(klant.gebruikersnaam()).isEqualTo(new BigDecimal(verwacht.getValue()));
        }
    }

    /** Een maand zonder rente krijgt geen overboeking: die moet groter zijn dan € 0,00. */
    @Test
    void zonderSpaargeldIsErGeenRente() {
        var geen = new DemoKlanten.DemoKlant("geen", "Geen", Bedrag.van("0.00"), GEEN_SPAARGELD,
                new DemoKlanten.VasteBoeking("Werkgever", Bedrag.van("100.00")), Optional.empty(), List.of(), Optional.empty());

        assertThat(Generator.rente(geen)).isEmpty();
    }

    /** FO: webwinkels 1–4 keer per maand. pvisser heeft geen woonlasten en komt dus nooit in de buurt van een tekort. */
    @Test
    void eenKlantZonderTekortKooptElkeMaand1Tot4KeerBijEenWebwinkel() {
        Map<YearMonth, Long> perMaand = webwinkelaankopen(klant("pvisser")).stream()
                .collect(Collectors.groupingBy(boeking -> YearMonth.from(boeking.tijdstip()), Collectors.counting()));

        assertThat(perMaand).hasSize(MAANDEN);
        assertThat(perMaand.values()).allSatisfy(aantal -> assertThat(aantal).isBetween(1L, 4L));
        assertThat(perMaand.values()).contains(1L, 4L);
    }

    /** FO: een bedrag van € 10–250 aan een webwinkel, via iDEAL | Wero, tussen 8:00 en 21:00, zonder tegen-IBAN. */
    @Test
    void eenWebwinkelaankoopHeeftHetBedragDeTegenpartijEnHetTijdstipUitHetFo() {
        for (DemoKlanten.DemoKlant klant : DemoKlanten.ALLE) {
            assertThat(webwinkelaankopen(klant)).as(klant.gebruikersnaam()).isNotEmpty().allSatisfy(boeking -> {
                assertThat(boeking.bedrag().waarde()).isBetween(new BigDecimal("-250.00"), new BigDecimal("-10.00"));
                assertThat(boeking.tegenpartij()).isIn("Webwinkel Alles", "BoekenBox", "ModeOnline", "ElektroShop");
                assertThat(boeking.tijdstip().toLocalTime()).isBetween(LocalTime.of(8, 0), LocalTime.of(21, 0));
                assertThat(boeking.tegenIban()).isEmpty();
            });
        }
    }

    /** De grenzen worden ook echt gehaald: alle vier de webwinkels, en bedragen en dagen over het hele bereik. */
    @Test
    void deWebwinkelaankopenVarierenOverHetHeleBereik() {
        List<Boeking> alle = DemoKlanten.ALLE.stream().flatMap(klant -> webwinkelaankopen(klant).stream()).toList();

        assertThat(alle).extracting(Boeking::tegenpartij).contains("Webwinkel Alles", "BoekenBox", "ModeOnline", "ElektroShop");
        assertThat(alle).extracting(boeking -> boeking.bedrag().waarde()).anyMatch(bedrag -> bedrag.compareTo(new BigDecimal("-15.00")) > 0);
        assertThat(alle).extracting(boeking -> boeking.bedrag().waarde()).anyMatch(bedrag -> bedrag.compareTo(new BigDecimal("-245.00")) < 0);
        assertThat(alle).extracting(boeking -> boeking.tijdstip().getDayOfMonth()).contains(1, 31);
    }

    /** Vaste seed per klant (ADR 0041). */
    @Test
    void elkeKlantHeeftEigenWebwinkelaankopen() {
        assertThat(webwinkelaankopen(klant("pvisser"))).isNotEqualTo(webwinkelaankopen(klant("rmulder")));
    }

    /** FO: een niet-vaste uitgave vervalt bij een dreigend tekort; tot het salaris op de 24e staat er € 0,00. */
    @Test
    void bijEenDreigendTekortVervaltEenWebwinkelaankoop() {
        var krap = new DemoKlanten.DemoKlant("krap", "Krap", Bedrag.van("950.00"), GEEN_SPAARGELD,
                new DemoKlanten.VasteBoeking("Werkgever", Bedrag.van("3100.00")),
                huur("950.00"), List.of(), Optional.empty());

        assertThat(webwinkelaankopen(krap))
                .isNotEmpty()
                .filteredOn(boeking -> boeking.tijdstip().isBefore(LocalDateTime.of(2021, 10, 24, 0, 0)))
                .isEmpty();
    }

    /** De webwinkels gebruiken wat er na de boodschappen over is: samen maken ze de rekening nooit rood. */
    @Test
    void boodschappenEnWebwinkelaankopenSamenMakenDeRekeningNietRood() {
        var krap = new DemoKlanten.DemoKlant("krap", "Krap", Bedrag.van("1000.00"), GEEN_SPAARGELD,
                new DemoKlanten.VasteBoeking("Werkgever", Bedrag.van("800.00")),
                huur("100.00"), List.of(), Optional.empty());

        BigDecimal lopend = krap.beginsaldo().waarde();
        Map<LocalDate, BigDecimal> perDag = new TreeMap<>();
        Generator.boekingen(krap).forEach(boeking -> perDag.merge(boeking.datum(), boeking.bedrag().waarde(), BigDecimal::add));
        for (BigDecimal beweging : perDag.values()) {
            lopend = lopend.add(beweging);
            assertThat(lopend).isGreaterThanOrEqualTo(BigDecimal.ZERO);
        }
        assertThat(boodschappen(krap)).isNotEmpty();
        assertThat(webwinkelaankopen(krap)).isNotEmpty();
    }

    /** In 2021 gaat het salaris van de 24e de 25e helemaal naar de spaarrekening, dus er is niets over voor webwinkels. */
    @Test
    void eenWebwinkelaankoopMagEenLatereInlegNietRoodMaken() {
        assertThat(webwinkelaankopen(spaarder("100.00"))).filteredOn(aankoop -> aankoop.tijdstip().getYear() == 2021).isEmpty();
    }

    /** FO: contant geld 0–2 keer per maand. pvisser heeft geen woonlasten en komt dus nooit in de buurt van een tekort. */
    @Test
    void eenKlantZonderTekortNeemtElkeMaandHoogstensTweeKeerContantGeldOp() {
        Map<YearMonth, Long> perMaand = geldopnames(klant("pvisser")).stream()
                .collect(Collectors.groupingBy(boeking -> YearMonth.from(boeking.tijdstip()), Collectors.counting()));

        assertThat(perMaand.values()).allSatisfy(aantal -> assertThat(aantal).isBetween(1L, 2L));
        assertThat(perMaand.values()).contains(1L, 2L);
        assertThat(perMaand).hasSizeLessThan(MAANDEN);
    }

    /** FO, origineel: € 20–200 in hele tientjes, bij een geldautomaat, tussen 8:00 en 21:00, zonder tegen-IBAN. */
    @Test
    void eenGeldopnameHeeftHetBedragDeTegenpartijEnHetTijdstipUitHetFo() {
        for (DemoKlanten.DemoKlant klant : DemoKlanten.ALLE) {
            assertThat(geldopnames(klant)).as(klant.gebruikersnaam()).isNotEmpty().allSatisfy(boeking -> {
                assertThat(boeking.bedrag().waarde()).isBetween(new BigDecimal("-200.00"), new BigDecimal("-20.00"));
                assertThat(boeking.bedrag().waarde().remainder(BigDecimal.TEN)).isEqualByComparingTo(BigDecimal.ZERO);
                assertThat(boeking.tegenpartij()).isIn("Geldautomaat Utrecht Centrum", "Geldautomaat Amersfoort Centrum", "Geldautomaat Zuidstad Centrum");
                assertThat(boeking.tijdstip().toLocalTime()).isBetween(LocalTime.of(8, 0), LocalTime.of(21, 0));
                assertThat(boeking.tegenIban()).isEmpty();
            });
        }
    }

    /** De grenzen worden ook echt gehaald: alle drie de geldautomaten, en de laagste en hoogste opname. */
    @Test
    void deGeldopnamesVarierenOverHetHeleBereik() {
        List<Boeking> alle = DemoKlanten.ALLE.stream().flatMap(klant -> geldopnames(klant).stream()).toList();

        assertThat(alle).extracting(Boeking::tegenpartij).contains("Geldautomaat Utrecht Centrum", "Geldautomaat Amersfoort Centrum", "Geldautomaat Zuidstad Centrum");
        assertThat(alle).extracting(boeking -> boeking.bedrag().waarde()).contains(new BigDecimal("-20.00"), new BigDecimal("-200.00"));
    }

    /** Vaste seed per klant (ADR 0041). */
    @Test
    void elkeKlantHeeftEigenGeldopnames() {
        assertThat(geldopnames(klant("pvisser"))).isNotEqualTo(geldopnames(klant("rmulder")));
    }

    /** FO: een niet-vaste uitgave vervalt bij een dreigend tekort; tot het salaris op de 24e staat er € 0,00. */
    @Test
    void bijEenDreigendTekortVervaltEenGeldopname() {
        var krap = new DemoKlanten.DemoKlant("krap", "Krap", Bedrag.van("950.00"), GEEN_SPAARGELD,
                new DemoKlanten.VasteBoeking("Werkgever", Bedrag.van("3100.00")),
                huur("950.00"), List.of(), Optional.empty());

        assertThat(geldopnames(krap))
                .isNotEmpty()
                .filteredOn(boeking -> boeking.tijdstip().isBefore(LocalDateTime.of(2021, 10, 24, 0, 0)))
                .isEmpty();
    }

    /** Een opname bij de geldautomaat vervalt bij een tekort, en haalt dus nooit geld van de spaarrekening. */
    @Test
    void eenGeldopnameLeidtNietTotEenOpnameVanDeSpaarrekening() {
        var krap = new DemoKlanten.DemoKlant("krap", "Krap", Bedrag.van("950.00"), Bedrag.van("5000.00"),
                new DemoKlanten.VasteBoeking("Werkgever", Bedrag.van("3100.00")),
                huur("950.00"), List.of(), Optional.empty());

        assertThat(geldopnames(krap)).isNotEmpty();
        assertThat(Generator.opnames(krap)).isEmpty();
    }

    private static List<Generator.BetalingTussenKlanten> betalingenTussenKlanten() {
        return Generator.betalingenTussenKlanten(DemoKlanten.ALLE);
    }

    private static DemoKlanten.DemoKlant metInkomen(String gebruikersnaam, String naam, String beginsaldo, String inkomen, String huur) {
        return new DemoKlanten.DemoKlant(gebruikersnaam, naam, Bedrag.van(beginsaldo), GEEN_SPAARGELD,
                new DemoKlanten.VasteBoeking("Werkgever", Bedrag.van(inkomen)),
                huur(huur), List.of(), Optional.empty());
    }

    /** FO: 0–3 keer per maand; alleen wat een klant zelf betaalt, telt mee. */
    @Test
    void eenKlantBetaaltElkeMaandHoogstensDrieKeerAanEenAndereKlant() {
        Map<YearMonth, Long> perMaand = betalingenTussenKlanten().stream()
                .filter(betaling -> betaling.van().equals("pvisser"))
                .collect(Collectors.groupingBy(betaling -> YearMonth.from(betaling.tijdstip()), Collectors.counting()));

        assertThat(perMaand.values()).allSatisfy(aantal -> assertThat(aantal).isBetween(1L, 3L));
        assertThat(perMaand.values()).contains(1L, 2L, 3L);
        assertThat(perMaand).hasSizeLessThan(MAANDEN);
    }

    /** FO: € 5–150, tussen 8:00 en 21:00, nooit aan zichzelf, en de ontvanger is een echte demo-klant. */
    @Test
    void eenBetalingTussenKlantenHeeftHetBedragEnHetTijdstipUitHetFo() {
        List<String> gebruikersnamen = DemoKlanten.ALLE.stream().map(DemoKlanten.DemoKlant::gebruikersnaam).toList();

        assertThat(betalingenTussenKlanten()).isNotEmpty().allSatisfy(betaling -> {
            assertThat(betaling.bedrag().waarde()).isBetween(new BigDecimal("5.00"), new BigDecimal("150.00"));
            assertThat(betaling.tijdstip().toLocalTime()).isBetween(LocalTime.of(8, 0), LocalTime.of(21, 0));
            assertThat(betaling.naar()).isNotEqualTo(betaling.van()).isIn(gebruikersnamen);
            assertThat(betaling.van()).isIn(gebruikersnamen);
        });
    }

    /** De grenzen worden ook echt gehaald, en elke klant betaalt en ontvangt. */
    @Test
    void deBetalingenTussenKlantenVarierenOverHetHeleBereik() {
        List<Generator.BetalingTussenKlanten> alle = betalingenTussenKlanten();
        List<String> gebruikersnamen = DemoKlanten.ALLE.stream().map(DemoKlanten.DemoKlant::gebruikersnaam).toList();

        assertThat(alle).extracting(Generator.BetalingTussenKlanten::van).containsAll(gebruikersnamen);
        assertThat(alle).extracting(Generator.BetalingTussenKlanten::naar).containsAll(gebruikersnamen);
        assertThat(alle).extracting(betaling -> betaling.bedrag().waarde()).anyMatch(bedrag -> bedrag.compareTo(new BigDecimal("6.00")) < 0);
        assertThat(alle).extracting(betaling -> betaling.bedrag().waarde()).anyMatch(bedrag -> bedrag.compareTo(new BigDecimal("149.00")) > 0);
    }

    /** ADR 0041: de vaste seed geeft elke installatie dezelfde betalingen; elke klant heeft zijn eigen reeks. */
    @Test
    void deBetalingenTussenKlantenLiggenVastDoorDeSeed() {
        assertThat(betalingenTussenKlanten()).isEqualTo(betalingenTussenKlanten());

        Map<String, List<BigDecimal>> perKlant = betalingenTussenKlanten().stream().collect(Collectors.groupingBy(
                Generator.BetalingTussenKlanten::van, Collectors.mapping(betaling -> betaling.bedrag().waarde(), Collectors.toList())));
        assertThat(perKlant.get("pvisser")).isNotEqualTo(perKlant.get("rmulder"));
    }

    /** ADR 0044, FO: alles samen, ook de betalingen en wat een klant ontvangt, maakt geen enkele betaalrekening rood. */
    @Test
    void geenBetaalrekeningStaatRoodInclusiefDeBetalingenTussenKlanten() {
        for (DemoKlanten.DemoKlant klant : DemoKlanten.ALLE) {
            Map<LocalDate, BigDecimal> perDag = new TreeMap<>();
            Generator.boekingen(klant).forEach(boeking -> perDag.merge(boeking.datum(), boeking.bedrag().waarde(), BigDecimal::add));
            Generator.inleggen(klant).forEach(inleg -> perDag.merge(inleg.tijdstip().toLocalDate(), inleg.bedrag().waarde().negate(), BigDecimal::add));
            Generator.opnames(klant).forEach(opname -> perDag.merge(opname.tijdstip().toLocalDate(), opname.bedrag().waarde(), BigDecimal::add));
            betalingenTussenKlanten().stream().filter(betaling -> betaling.van().equals(klant.gebruikersnaam()))
                    .forEach(betaling -> perDag.merge(betaling.tijdstip().toLocalDate(), betaling.bedrag().waarde().negate(), BigDecimal::add));

            BigDecimal lopend = klant.beginsaldo().waarde();
            for (BigDecimal beweging : perDag.values()) {
                lopend = lopend.add(beweging);
                assertThat(lopend).as(klant.gebruikersnaam()).isGreaterThanOrEqualTo(BigDecimal.ZERO);
            }
        }
    }

    /** FO: een niet-vaste uitgave vervalt bij een dreigend tekort; tot het salaris op de 24e staat er € 0,00. */
    @Test
    void bijEenDreigendTekortVervaltEenBetalingAanEenAndereKlant() {
        var krap = metInkomen("krap", "Krap", "950.00", "3100.00", "950.00");
        var ander = metInkomen("ander", "Ander", "950.00", "3100.00", "950.00");

        assertThat(Generator.betalingenTussenKlanten(List.of(krap, ander)))
                .filteredOn(betaling -> betaling.van().equals("krap"))
                .isNotEmpty()
                .filteredOn(betaling -> betaling.tijdstip().isBefore(LocalDateTime.of(2021, 10, 24, 0, 0)))
                .isEmpty();
    }

    /** ADR 0044: ontvangen geld telt niet mee als ruimte om zelf te betalen, ook als het op tijd binnenkomt. */
    @Test
    void ontvangenGeldTeltNietMeeAlsRuimteOmZelfTeBetalen() {
        // € 0,01 × 1,26 is afgerond nog steeds € 0,01, dus de loonstijging geeft deze klant geen ruimte.
        var zonderGeld = metInkomen("zonder", "Zonder", "1.00", "0.01", "0.01");
        var rijk = metInkomen("rijk", "Rijk", "100000.00", "9000.00", "100.00");

        List<Generator.BetalingTussenKlanten> betalingen = Generator.betalingenTussenKlanten(List.of(zonderGeld, rijk));

        assertThat(betalingen).extracting(Generator.BetalingTussenKlanten::van).containsOnly("rijk");
        assertThat(betalingen).extracting(Generator.BetalingTussenKlanten::naar).containsOnly("zonder");
    }

    @Test
    void eenWerknemerKrijgtElkeMeiVakantiegeldOpDe24e() {
        var bedragen = Map.of(2022, "2966.08", 2023, "3194.24", 2024, "3365.36", 2025, "3479.44", 2026, "3593.52");
        var verwacht = IntStream.rangeClosed(2022, 2026)
                .mapToObj(jaar -> new Boeking(LocalDateTime.of(jaar, 5, 24, 7, 0), Bedrag.van(bedragen.get(jaar)), "Techniek Noord B.V.",
                        TransactieType.VERZAMELBETALING, Optional.empty()))
                .toList();

        assertThat(vakantiegeld(klant("jdevries"))).isEqualTo(verwacht);
    }

    @Test
    void hetVakantiegeldIs92ProcentVanHetInkomenVanDatJaar() {
        var bedragen = DemoKlanten.ALLE.stream()
                .filter(klant -> klant.soort() == DemoKlanten.Soort.WERKNEMER)
                .collect(Collectors.toMap(DemoKlanten.DemoKlant::gebruikersnaam, klant -> vakantiegeld(klant).getFirst().bedrag()));

        assertThat(bedragen).isEqualTo(Map.of(
                "jdevries", Bedrag.van("2966.08"),
                "sbakker", Bedrag.van("4592.64"),
                "melamrani", Bedrag.van("4018.56"),
                "ljansen", Bedrag.van("2248.48"),
                "fyilmaz", Bedrag.van("4975.36"),
                "edeboer", Bedrag.van("3731.52"),
                "nhendriks", Bedrag.van("5740.80")));
    }

    @Test
    void hetVakantiegeldVanEenKromInkomenWordtAfgerondOpCenten() {
        var werknemer = new DemoKlanten.DemoKlant("krom", "Krom", Bedrag.van("0.00"), GEEN_SPAARGELD,
                new DemoKlanten.VasteBoeking("Werkgever", Bedrag.van("100.01")), Optional.empty(), List.of(), Optional.empty(), DemoKlanten.Soort.WERKNEMER, Optional.empty());

        // In 2022 is het salaris € 104,01 (€ 100,01 × 1,04), en 92% daarvan is € 95,6892.
        assertThat(vakantiegeld(werknemer).getFirst().bedrag()).isEqualTo(Bedrag.van("95.69"));
    }

    @Test
    void wieGeenWerknemerIsKrijgtGeenVakantiegeld() {
        assertThat(List.of("pvisser", "dsmit", "rmulder")).allSatisfy(gebruikersnaam ->
                assertThat(vakantiegeld(klant(gebruikersnaam))).as(gebruikersnaam).isEmpty());
    }

    @Test
    void eenAankoopKanHetVakantiegeldPasNaOntvangstUitgeven() {
        // Tot en met 2022 is er geen eigen ruimte, dus het enige geld is het vakantiegeld van 24 mei 2022 en de teruggave.
        var krap = krap("krap", DemoKlanten.Soort.WERKNEMER);

        assertThat(boodschappen(krap)).isNotEmpty();
        assertThat(boodschappen(krap)).allSatisfy(boodschap -> assertThat(boodschap.datum()).isAfterOrEqualTo(LocalDate.of(2022, 5, 24)));
    }

    private static List<Generator.Inleg> extraInleggen(DemoKlanten.DemoKlant klant) {
        return Generator.inleggen(klant).stream().filter(inleg -> inleg.tijdstip().getDayOfMonth() != 25).toList();
    }

    /** Origineel: na het vakantiegeld, op 27 mei tussen 9:00 en 11:00, 40% van het salaris naar de spaarrekening. */
    @Test
    void eenWerknemerLegtElkeMeiOpDe27eExtraIn() {
        assertThat(extraInleggen(klant("jdevries"))).extracting(Generator.Inleg::bedrag).containsExactly(Bedrag.van("1289.60"),
                Bedrag.van("1388.80"), Bedrag.van("1463.20"), Bedrag.van("1512.80"), Bedrag.van("1562.40"));
        assertThat(extraInleggen(klant("jdevries"))).hasSize(5).allSatisfy(inleg -> {
            assertThat(inleg.tijdstip().getMonth()).isEqualTo(Month.MAY);
            assertThat(inleg.tijdstip().getDayOfMonth()).isEqualTo(27);
            assertThat(inleg.tijdstip().toLocalTime()).isBetween(LocalTime.of(9, 0), LocalTime.of(11, 0));
        });
        assertThat(extraInleggen(klant("jdevries"))).extracting(inleg -> inleg.tijdstip().getYear()).containsExactly(2022, 2023, 2024, 2025, 2026);
    }

    @Test
    void deExtraInlegIs40ProcentVanHetSalarisVanDatJaar() {
        var bedragen = DemoKlanten.ALLE.stream()
                .filter(klant -> klant.soort() == DemoKlanten.Soort.WERKNEMER)
                .collect(Collectors.toMap(DemoKlanten.DemoKlant::gebruikersnaam, klant -> extraInleggen(klant).getFirst().bedrag()));

        assertThat(bedragen).isEqualTo(Map.of(
                "jdevries", Bedrag.van("1289.60"),
                "sbakker", Bedrag.van("1996.80"),
                "melamrani", Bedrag.van("1747.20"),
                "ljansen", Bedrag.van("977.60"),
                "fyilmaz", Bedrag.van("2163.20"),
                "edeboer", Bedrag.van("1622.40"),
                "nhendriks", Bedrag.van("2496.00")));
    }

    @Test
    void deExtraInlegVanEenKromSalarisWordtAfgerondOpCenten() {
        var werknemer = new DemoKlanten.DemoKlant("krom", "Krom", Bedrag.van("0.00"), GEEN_SPAARGELD,
                new DemoKlanten.VasteBoeking("Werkgever", Bedrag.van("100.04")), Optional.empty(), List.of(), Optional.empty(), DemoKlanten.Soort.WERKNEMER, Optional.empty());

        // In 2022 is het salaris € 104,04 (€ 100,04 × 1,04 = € 104,0416), en 40% daarvan is € 41,616.
        assertThat(extraInleggen(werknemer).getFirst().bedrag()).isEqualTo(Bedrag.van("41.62"));
    }

    @Test
    void wieGeenWerknemerIsLegtNietExtraIn() {
        assertThat(List.of("pvisser", "dsmit", "rmulder")).allSatisfy(gebruikersnaam ->
                assertThat(extraInleggen(klant(gebruikersnaam))).as(gebruikersnaam).isEmpty());
    }

    /** ADR 0041: de vaste seed bepaalt het tijdstip, dus elke installatie geeft Jan hetzelfde. */
    @Test
    void deEersteExtraInlegVanJanLigtVastDoorDeSeed() {
        assertThat(extraInleggen(klant("jdevries")).getFirst().tijdstip()).isEqualTo(LocalDateTime.of(2022, 5, 27, 9, 15));
    }

    /** Met genoeg geld vanaf het begin past ook een inleg vóór oktober 2021, maar de data begint pas in mei 2022. */
    @Test
    void deEersteExtraInlegIsInMei2022() {
        var rijk = new DemoKlanten.DemoKlant("rijk", "Rijk", Bedrag.van("100000.00"), GEEN_SPAARGELD,
                new DemoKlanten.VasteBoeking("Werkgever", Bedrag.van("100.00")), Optional.empty(), List.of(), Optional.empty(), DemoKlanten.Soort.WERKNEMER, Optional.empty());

        assertThat(extraInleggen(rijk)).extracting(inleg -> inleg.tijdstip().getYear()).containsExactly(2022, 2023, 2024, 2025, 2026);
    }

    /**
     * Op 1 oktober 2021 komt er € 10.100,00 van de spaarrekening voor de hypotheek van € 10.000,00. Zonder die opname zou de rekening
     * na het vakantiegeld van € 9.200,00 en de teruggave van hooguit € 1.200,00 geen ruimte hebben voor € 4.000,00.
     */
    @Test
    void deOpnamesTellenMeeVoorDeExtraInleg() {
        var klant = new DemoKlanten.DemoKlant("aangevuld", "Aangevuld", Bedrag.van("0.00"), Bedrag.van("20000.00"),
                new DemoKlanten.VasteBoeking("Werkgever", Bedrag.van("10000.00")), hypotheek("10000.00"), List.of(), Optional.empty(),
                DemoKlanten.Soort.WERKNEMER, Optional.empty());

        assertThat(Generator.opnames(klant)).containsExactly(opname(10, 1, "10100.00"));
        assertThat(extraInleggen(klant)).extracting(inleg -> inleg.tijdstip().getYear()).contains(2022);
    }

    /**
     * Een hypotheek van € 11.400,00 op de 1e, en in 2022 een salaris van € 10.400,00: elke maand € 1.000,00 tekort. Het beginsaldo is op
     * 1 mei 2022 precies op. Het vakantiegeld van € 9.568,00 dekt de maanden tot november, maar niet ook nog de extra inleg van
     * € 4.160,00. Die vervalt dan, net als een aankoop, en vraagt geen opname.
     */
    @Test
    void eenExtraInlegDieRoodZouMakenVervaltZonderOpname() {
        var krap = new DemoKlanten.DemoKlant("krap", "Krap", Bedrag.van("19600.00"), GEEN_SPAARGELD,
                new DemoKlanten.VasteBoeking("Werkgever", Bedrag.van("10000.00")), hypotheek("11400.00"), List.of(), Optional.empty(),
                DemoKlanten.Soort.WERKNEMER, Optional.empty());

        assertThat(extraInleggen(krap)).extracting(inleg -> inleg.tijdstip().getYear()).doesNotContain(2022);
        assertThat(Generator.opnames(krap)).isEmpty();
        assertThat(Generator.boekingen(krap)).isNotEmpty();
    }

    /** Origineel: één keer per jaar in april, mei of juni, op dag 1 t/m 28, tussen 1:00 en 6:00, € 80–1.200, als overschrijving. */
    @Test
    void elkeKlantBehalveDeZzperKrijgtElkJaarEenTeruggaveZoalsInHetOrigineel() {
        var ontvangers = DemoKlanten.ALLE.stream().filter(klant -> !klant.gebruikersnaam().equals("dsmit")).toList();

        assertThat(ontvangers).hasSize(9).allSatisfy(klant -> {
            assertThat(teruggaven(klant)).extracting(boeking -> boeking.tijdstip().getYear()).containsExactly(2022, 2023, 2024, 2025, 2026);
            assertThat(teruggaven(klant)).allSatisfy(teruggave -> {
                assertThat(teruggave.tijdstip().getMonthValue()).isBetween(4, 6);
                assertThat(teruggave.tijdstip().getDayOfMonth()).isBetween(1, 28);
                assertThat(teruggave.tijdstip().toLocalTime()).isBetween(LocalTime.of(1, 0), LocalTime.of(6, 0));
                assertThat(teruggave.bedrag().waarde()).isBetween(new BigDecimal("80.00"), new BigDecimal("1200.00"));
                assertThat(teruggave.type()).isEqualTo(TransactieType.OVERSCHRIJVING);
                assertThat(teruggave.tegenIban()).isEmpty();
            });
        });
    }

    @Test
    void eenZzperKrijgtGeenTeruggave() {
        assertThat(teruggaven(klant("dsmit"))).isEmpty();
    }

    @Test
    void deTeruggavenVarierenOverHetHeleBereik() {
        var alle = IntStream.range(0, 400).mapToObj(i -> krap("klant" + i, DemoKlanten.Soort.GEPENSIONEERD)).flatMap(klant -> teruggaven(klant).stream()).toList();

        assertThat(alle).extracting(teruggave -> teruggave.tijdstip().getMonthValue()).containsOnly(4, 5, 6);
        assertThat(alle).extracting(teruggave -> teruggave.tijdstip().getDayOfMonth()).contains(1, 28).doesNotContain(29, 30, 31);
        assertThat(alle).extracting(teruggave -> teruggave.tijdstip().toLocalTime()).anyMatch(tijd -> tijd.isBefore(LocalTime.of(1, 10)));
        assertThat(alle).extracting(teruggave -> teruggave.tijdstip().toLocalTime()).anyMatch(tijd -> tijd.isAfter(LocalTime.of(5, 50)));
        assertThat(alle).extracting(teruggave -> teruggave.bedrag().waarde()).anyMatch(bedrag -> bedrag.compareTo(new BigDecimal("85.00")) < 0);
        assertThat(alle).extracting(teruggave -> teruggave.bedrag().waarde()).anyMatch(bedrag -> bedrag.compareTo(new BigDecimal("1195.00")) > 0);
    }

    /** ADR 0041: de vaste seed geeft elke installatie dezelfde teruggaven; elke klant heeft zijn eigen reeks. */
    @Test
    void deTeruggavenLiggenVastDoorDeSeed() {
        assertThat(teruggaven(klant("pvisser"))).isEqualTo(teruggaven(klant("pvisser")));
        assertThat(teruggaven(klant("pvisser"))).isNotEqualTo(teruggaven(klant("rmulder")));
    }

    @Test
    void deEersteTeruggaveVanJanLigtVastDoorDeSeed() {
        assertThat(teruggaven(klant("jdevries")).getFirst()).isEqualTo(new Boeking(LocalDateTime.of(2022, 4, 17, 2, 17), Bedrag.van("555.84"),
                "Belastingdienst", TransactieType.OVERSCHRIJVING, Optional.empty()));
    }

    @Test
    void eenAankoopKanDeTeruggavePasNaOntvangstUitgeven() {
        // Tot en met 2022 is er geen eigen ruimte, dus het enige geld is de teruggave van 2022.
        var krap = krap("krap", DemoKlanten.Soort.GEPENSIONEERD);
        LocalDate eerste = teruggaven(krap).getFirst().datum();

        assertThat(boodschappen(krap)).isNotEmpty();
        assertThat(boodschappen(krap)).allSatisfy(boodschap -> assertThat(boodschap.datum()).isAfterOrEqualTo(eerste));
    }

    /** Origineel: de termijn stijgt elk jaar met de inflatie, net als de vaste lasten. */
    @Test
    void deTermijnStijgtElkJaarMetDeInflatie() {
        assertThat(bedragPerJaar(klant("jdevries"), "Gemeente Zuidstad")).isEqualTo(Map.of(2021, Bedrag.van("-38.00"), 2022, Bedrag.van("-41.80"),
                2023, Bedrag.van("-44.08"), 2024, Bedrag.van("-45.60"), 2025, Bedrag.van("-47.12"), 2026, Bedrag.van("-48.64")));
        assertThat(bedragPerJaar(klant("sbakker"), "Gemeente Zuidstad")).containsEntry(2026, Bedrag.van("-70.40"));
    }

    /** Origineel: 10 termijnen, van maart t/m december, op de 28e, als incasso. */
    @Test
    void janBetaaltIn2022TienTermijnenVanMaartTotEnMetDecember() {
        var verwacht = IntStream.rangeClosed(3, 12).mapToObj(maand -> incasso(2022, maand, 28, "-41.80", "Gemeente Zuidstad")).toList();

        assertThat(termijnen(klant("jdevries")).stream().filter(termijn -> termijn.tijdstip().getYear() == 2022)).isEqualTo(verwacht);
    }

    @Test
    void elkeKlantBetaalt53TermijnenMetIn2021HetBedragVanZijnHuishouden() {
        var perKlant = DemoKlanten.ALLE.stream().collect(Collectors.toMap(DemoKlanten.DemoKlant::gebruikersnaam, klant -> {
            assertThat(termijnen(klant)).as(klant.gebruikersnaam()).hasSize(53);
            return termijnen(klant).getFirst().bedrag();
        }));

        assertThat(perKlant).isEqualTo(Map.of(
                "jdevries", Bedrag.van("-38.00"), "ljansen", Bedrag.van("-38.00"), "dsmit", Bedrag.van("-38.00"), "rmulder", Bedrag.van("-38.00"),
                "sbakker", Bedrag.van("-55.00"), "melamrani", Bedrag.van("-55.00"), "pvisser", Bedrag.van("-55.00"), "fyilmaz", Bedrag.van("-55.00"),
                "edeboer", Bedrag.van("-55.00"), "nhendriks", Bedrag.van("-55.00")));
    }

    /** Een vaste last vervalt niet (FO): zou de termijn de rekening rood maken, dan faalt de generator. */
    @Test
    void eenTermijnDieRoodZouMakenGeeftEenFout() {
        assertThatThrownBy(() -> Generator.boekingen(metTermijn("37.99"))).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void eenBoodschapMagEenLatereTermijnNietRoodMaken() {
        // Het inkomen gaat op aan de termijn, dus er is pas geld over als de termijnen in januari stoppen.
        assertThat(boodschappen(metTermijn("38.00"))).isNotEmpty();
        assertThat(boodschappen(metTermijn("38.00"))).allSatisfy(boodschap -> assertThat(boodschap.datum()).isAfterOrEqualTo(LocalDate.of(2022, 1, 24)));
    }
}
