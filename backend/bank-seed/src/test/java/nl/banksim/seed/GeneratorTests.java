package nl.banksim.seed;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.YearMonth;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.stream.Collectors;

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

    /** Alleen het inkomen en de woonlasten. */
    private static List<Boeking> vasteBoekingen(DemoKlanten.DemoKlant klant) {
        return Generator.boekingen(klant).stream()
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
                woonlasten(LocalDateTime.of(2026, 12, 1, 6, 0), "-950.00", "Woonstichting Thuis"),
                inkomen(LocalDateTime.of(2026, 12, 24, 7, 0), "3100.00", "Techniek Noord B.V."));
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
                Optional.of(new DemoKlanten.VasteBoeking("Verhuurder", Bedrag.van("950.00"))), List.of(), Optional.empty());

        assertThatThrownBy(() -> Generator.boekingen(teWeinig))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("rood");
    }

    /** Precies op € 0,00 is niet rood. */
    @Test
    void eenKlantDiePreciesOpNulKomtMag() {
        var precies = new DemoKlanten.DemoKlant("nul", "Nul", Bedrag.van("950.00"), GEEN_SPAARGELD,
                new DemoKlanten.VasteBoeking("Werkgever", Bedrag.van("3100.00")),
                Optional.of(new DemoKlanten.VasteBoeking("Verhuurder", Bedrag.van("950.00"))), List.of(), Optional.empty());

        assertThat(vasteBoekingen(precies)).hasSize(2 * MAANDEN);
    }

    /** Elke dag telt: pas later in de maand komt het inkomen binnen. */
    @Test
    void eenDipTussenTweeBoekingenTeltOokAlsHetEindsaldoPositiefIs() {
        var dip = new DemoKlanten.DemoKlant("dip", "Dip", Bedrag.van("100.00"), GEEN_SPAARGELD,
                new DemoKlanten.VasteBoeking("Werkgever", Bedrag.van("3100.00")),
                Optional.of(new DemoKlanten.VasteBoeking("Verhuurder", Bedrag.van("950.00"))), List.of(), Optional.empty());

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
                Optional.of(new DemoKlanten.VasteBoeking("Verhuurder", Bedrag.van("950.00"))), List.of(), Optional.empty());

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
                Optional.of(new DemoKlanten.VasteBoeking("Verhuurder", Bedrag.van("100.00"))), List.of(), Optional.empty());

        BigDecimal lopend = krap.beginsaldo().waarde();
        Map<LocalDate, BigDecimal> perDag = new TreeMap<>();
        Generator.boekingen(krap).forEach(boeking -> perDag.merge(boeking.datum(), boeking.bedrag().waarde(), BigDecimal::add));
        for (BigDecimal beweging : perDag.values()) {
            lopend = lopend.add(beweging);
            assertThat(lopend).isGreaterThanOrEqualTo(BigDecimal.ZERO);
        }
        assertThat(boodschappen(krap)).isNotEmpty();
    }

    /** FO, origineel: vroeg in de ochtend een incasso, per last op de eigen dag. */
    @Test
    void deVasteLastenVanJanInDecember2026() {
        assertThat(Generator.boekingen(klant("jdevries"))).containsAll(List.of(
                incasso(2026, 12, 1, "-140.00", "Zorgverzekeraar Gezond"),
                incasso(2026, 12, 1, "-18.00", "Verzekeringen Zeker"),
                incasso(2026, 12, 15, "-95.00", "Groenstroom Energie"),
                incasso(2026, 12, 20, "-45.00", "NetPlus Internet"),
                incasso(2026, 12, 20, "-7.50", "Waterbedrijf Midden"),
                incasso(2026, 12, 22, "-17.50", "Mobiel Direct")));
    }

    /** Per volwassene de zorgpremie en het mobiel, per persoon het water, en met een auto een duurdere verzekering. */
    @Test
    void deVasteLastenHangenAfVanHetHuishouden() {
        assertThat(Generator.boekingen(klant("melamrani"))).containsAll(List.of(
                incasso(2026, 12, 1, "-280.00", "Zorgverzekeraar Gezond"),
                incasso(2026, 12, 1, "-73.00", "Verzekeringen Zeker"),
                incasso(2026, 12, 15, "-175.00", "Groenstroom Energie"),
                incasso(2026, 12, 20, "-45.00", "NetPlus Internet"),
                incasso(2026, 12, 20, "-37.50", "Waterbedrijf Midden"),
                incasso(2026, 12, 22, "-35.00", "Mobiel Direct")));
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

    /** Origineel: elke maand op de 25e, tussen 9:00 en 11:00, een vast bedrag per klant. */
    @Test
    void elkeKlantLegtElkeMaandZijnVasteBedragIn() {
        Map<String, String> bedragen = Map.of("jdevries", "400.00", "sbakker", "600.00", "melamrani", "250.00",
                "ljansen", "150.00", "pvisser", "200.00", "fyilmaz", "500.00", "dsmit", "500.00", "edeboer", "450.00",
                "rmulder", "150.00", "nhendriks", "800.00");

        for (DemoKlanten.DemoKlant klant : DemoKlanten.ALLE) {
            List<Generator.Inleg> inleggen = Generator.inleggen(klant);

            assertThat(inleggen).as(klant.gebruikersnaam()).hasSize(MAANDEN).allSatisfy(inleg -> {
                assertThat(inleg.bedrag()).isEqualTo(Bedrag.van(bedragen.get(klant.gebruikersnaam())));
                assertThat(inleg.tijdstip().getDayOfMonth()).isEqualTo(25);
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

    /** Het salaris van de 24e gaat de 25e helemaal naar de spaarrekening, dus er is niets over voor boodschappen. */
    @Test
    void eenBoodschapMagEenLatereInlegNietRoodMaken() {
        assertThat(boodschappen(spaarder("100.00"))).isEmpty();
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
                new DemoKlanten.VasteBoeking("Werkgever", Bedrag.van(last)), Optional.empty(),
                List.of(new DemoKlanten.VasteLast(1, "Energie", Bedrag.van(last))), Optional.empty());
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
        for (var verwacht : Map.of("jdevries", "34879.82", "ljansen", "12099.71", "melamrani", "21844.08").entrySet()) {
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
                Optional.of(new DemoKlanten.VasteBoeking("Verhuurder", Bedrag.van("950.00"))), List.of(), Optional.empty());

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
                Optional.of(new DemoKlanten.VasteBoeking("Verhuurder", Bedrag.van("100.00"))), List.of(), Optional.empty());

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

    /** Het salaris van de 24e gaat de 25e helemaal naar de spaarrekening, dus er is niets over voor webwinkels. */
    @Test
    void eenWebwinkelaankoopMagEenLatereInlegNietRoodMaken() {
        assertThat(webwinkelaankopen(spaarder("100.00"))).isEmpty();
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
                Optional.of(new DemoKlanten.VasteBoeking("Verhuurder", Bedrag.van("950.00"))), List.of(), Optional.empty());

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
                Optional.of(new DemoKlanten.VasteBoeking("Verhuurder", Bedrag.van("950.00"))), List.of(), Optional.empty());

        assertThat(geldopnames(krap)).isNotEmpty();
        assertThat(Generator.opnames(krap)).isEmpty();
    }
}
