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
        var teWeinig = new DemoKlanten.DemoKlant("rood", "Rood", Bedrag.van("949.99"),
                new DemoKlanten.VasteBoeking("Werkgever", Bedrag.van("3100.00")),
                Optional.of(new DemoKlanten.VasteBoeking("Verhuurder", Bedrag.van("950.00"))), List.of(), Optional.empty());

        assertThatThrownBy(() -> Generator.boekingen(teWeinig))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("rood");
    }

    /** Precies op € 0,00 is niet rood. */
    @Test
    void eenKlantDiePreciesOpNulKomtMag() {
        var precies = new DemoKlanten.DemoKlant("nul", "Nul", Bedrag.van("950.00"),
                new DemoKlanten.VasteBoeking("Werkgever", Bedrag.van("3100.00")),
                Optional.of(new DemoKlanten.VasteBoeking("Verhuurder", Bedrag.van("950.00"))), List.of(), Optional.empty());

        assertThat(vasteBoekingen(precies)).hasSize(2 * MAANDEN);
    }

    /** Elke dag telt: pas later in de maand komt het inkomen binnen. */
    @Test
    void eenDipTussenTweeBoekingenTeltOokAlsHetEindsaldoPositiefIs() {
        var dip = new DemoKlanten.DemoKlant("dip", "Dip", Bedrag.van("100.00"),
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
        var krap = new DemoKlanten.DemoKlant("krap", "Krap", Bedrag.van("950.00"),
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
        var krap = new DemoKlanten.DemoKlant("krap", "Krap", Bedrag.van("1000.00"),
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
        var teWeinig = new DemoKlanten.DemoKlant("rood", "Rood", Bedrag.van("100.00"),
                new DemoKlanten.VasteBoeking("Werkgever", Bedrag.van("3100.00")), Optional.empty(),
                List.of(new DemoKlanten.VasteLast(1, "Energie", Bedrag.van("100.01"))), Optional.empty());

        assertThatThrownBy(() -> Generator.boekingen(teWeinig)).isInstanceOf(IllegalStateException.class);
    }

    /** Een boodschap houdt rekening met een vaste last later in de maand. */
    @Test
    void eenBoodschapMagEenLatereVasteLastNietRoodMaken() {
        var krap = new DemoKlanten.DemoKlant("krap", "Krap", Bedrag.van("100.00"),
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

    /** ADR 0042: de inleg is een overboeking, geen eenzijdige boeking. */
    @Test
    void deInlegStaatNietBijDeEenzijdigeBoekingen() {
        assertThat(Generator.boekingen(klant("jdevries"))).extracting(Boeking::tegenpartij).doesNotContain("Jan de Vries");
    }

    private static DemoKlanten.DemoKlant spaarder(String inleg) {
        return new DemoKlanten.DemoKlant("spaarder", "Spaarder", Bedrag.van("0.00"),
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
        var geen = new DemoKlanten.DemoKlant("geen", "Geen", Bedrag.van("0.00"),
                new DemoKlanten.VasteBoeking("Werkgever", Bedrag.van("100.00")), Optional.empty(), List.of(), Optional.empty());

        assertThat(Generator.inleggen(geen)).isEmpty();
    }
}
