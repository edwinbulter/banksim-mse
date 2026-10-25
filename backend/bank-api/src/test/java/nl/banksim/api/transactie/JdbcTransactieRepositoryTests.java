package nl.banksim.api.transactie;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import nl.banksim.domain.geld.Bedrag;
import nl.banksim.domain.rekening.Iban;
import nl.banksim.domain.transactie.TransactieType;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/** Tegen dezelfde PostgreSQL als in de chart, met de migraties die bank-api bij het starten uitvoert (ADR 0009). */
@Testcontainers
class JdbcTransactieRepositoryTests {

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(DockerImageName
            .parse("postgres:18.6-alpine@sha256:77f585114c32fbca283dc835b0596f4e52b51b4c6662d7810b2f4084f60a1873")
            .asCompatibleSubstituteFor("postgres"));

    static final Iban VAN_JAN = Iban.van("NL94SIMB0000000001");
    static final Iban VAN_PIET = Iban.van("NL38SIMB0123456789");

    static JdbcClient jdbc;

    @BeforeAll
    static void schemaRekeningenEnTransacties() {
        var dataSource = new DriverManagerDataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        Flyway.configure().dataSource(dataSource).load().migrate();
        jdbc = JdbcClient.create(dataSource);
        jdbc.sql("INSERT INTO rekeninghouder (gebruikersnaam, naam) VALUES ('jan', 'Jan'), ('piet', 'Piet')").update();
        jdbc.sql("""
                INSERT INTO rekening (iban, rekeninghouder_id, soort, openingssaldo)
                SELECT 'NL94SIMB0000000001', id, 'betaal', 1000.00 FROM rekeninghouder WHERE gebruikersnaam = 'jan'
                UNION ALL
                SELECT 'NL38SIMB0123456789', id, 'betaal', 5.00 FROM rekeninghouder WHERE gebruikersnaam = 'piet'
                """).update();
        // Bewust niet in datumvolgorde geboekt; twee transacties op 2 oktober.
        jdbc.sql("""
                INSERT INTO transactie (iban, datum, bedrag, tegenpartij, tijdstip, type, tegen_iban) VALUES
                ('NL94SIMB0000000001', '2026-10-02', 3215.00, 'Werkgever B.V.', '2026-10-02T09:15:00+02:00',
                 'ONLINE_BANKIEREN', 'NL20INGB0001234567'),
                ('NL94SIMB0000000001', '2026-10-01', -812.40, 'Woningstichting Thuis', '2026-10-01T00:30:00+02:00',
                 'INCASSO', 'NL91ABNA0417164300'),
                ('NL94SIMB0000000001', '2026-10-02', -12.50, 'Bakkerij De Korst', '2026-10-02T13:26:00+02:00',
                 'BETAALAUTOMAAT', NULL)
                """).update();
        jdbc.sql("UPDATE transactie SET omschrijving = 'Salaris oktober', betalingskenmerk = 'SAL-2026-10' WHERE tegenpartij = 'Werkgever B.V.'")
                .update();
    }

    /** Na alle transacties in deze test, zodat de tests niet afhangen van de dag waarop ze draaien. */
    static final LocalDate NA_ALLES = LocalDate.of(2026, 12, 31);

    private final JdbcTransactieRepository repository = new JdbcTransactieRepository(jdbc);

    /** ADR 0013: transacties na "vandaag" zijn onzichtbaar, ook voor de cursor; een transactie óp vandaag niet. */
    @Test
    void geeftAlleenDeTransactiesTotEnMetVandaag() {
        Pagina pagina = repository.vanRekening("jan", VAN_JAN, LocalDate.of(2026, 10, 1), Zoekfilter.GEEN, Optional.empty(), 1)
                .orElseThrow();

        assertThat(pagina.transacties()).extracting(Transactie::tegenpartij).containsExactly("Woningstichting Thuis");
        assertThat(pagina.volgende()).isEmpty();
    }

    @Test
    void geeftDeTransactiesVanNieuwNaarOudEnOpDezelfdeDagDeLaatstGeboekteEerst() {
        assertThat(repository.vanRekening("jan", VAN_JAN, NA_ALLES, Zoekfilter.GEEN, Optional.empty(), 50).orElseThrow().transacties())
                .extracting(Transactie::datum, Transactie::tegenpartij, Transactie::bedrag).containsExactly(
                        tuple(LocalDate.of(2026, 10, 2), "Bakkerij De Korst", Bedrag.van("-12.50")),
                        tuple(LocalDate.of(2026, 10, 2), "Werkgever B.V.", Bedrag.van("3215.00")),
                        tuple(LocalDate.of(2026, 10, 1), "Woningstichting Thuis", Bedrag.van("-812.40")));
    }

    /** De grens valt midden in 2 oktober: de volgende pagina begint bij de transactie ná de laatste getoonde. */
    @Test
    void dePaginaNaDeLaatsteGaatVerderBinnenDezelfdeDag() {
        Pagina eerste = repository.vanRekening("jan", VAN_JAN, NA_ALLES, Zoekfilter.GEEN, Optional.empty(), 1).orElseThrow();
        Pagina tweede = repository.vanRekening("jan", VAN_JAN, NA_ALLES, Zoekfilter.GEEN, eerste.volgende(), 1).orElseThrow();
        Pagina derde = repository.vanRekening("jan", VAN_JAN, NA_ALLES, Zoekfilter.GEEN, tweede.volgende(), 1).orElseThrow();

        assertThat(List.of(eerste, tweede, derde)).extracting(pagina -> pagina.transacties().getFirst().tegenpartij())
                .containsExactly("Bakkerij De Korst", "Werkgever B.V.", "Woningstichting Thuis");
        assertThat(eerste.volgende()).isPresent();
        assertThat(tweede.volgende()).isPresent();
        assertThat(derde.volgende()).isEmpty();
    }

    /**
     * S4.1: type, tijdstip, de rekeninghouder en de tegenpartij met IBAN; zonder tegen-IBAN is dat leeg. Het tijdstip
     * vlak na middernacht blijft 1 oktober, ook al is het in UTC nog 30 september.
     */
    @Test
    void geeftDeDetailsVanEenTransactie() {
        List<Transactie> transacties = repository.vanRekening("jan", VAN_JAN, NA_ALLES, Zoekfilter.GEEN, Optional.empty(), 50)
                .orElseThrow().transacties();

        Transactie incasso = transacties.get(2);
        assertThat(incasso.type()).isEqualTo(TransactieType.INCASSO);
        assertThat(incasso.tijdstip()).isEqualTo(OffsetDateTime.parse("2026-10-01T00:30:00+02:00"));
        assertThat(incasso.eigen()).isEqualTo(new Partij("Jan", Optional.of(VAN_JAN)));
        assertThat(incasso.tegen()).isEqualTo(new Partij("Woningstichting Thuis", Optional.of(Iban.van("NL91ABNA0417164300"))));
        assertThat(transacties.getFirst().tegen()).isEqualTo(new Partij("Bakkerij De Korst", Optional.empty()));
        assertThat(transacties.getFirst().type()).isEqualTo(TransactieType.BETAALAUTOMAAT);
        // S7.1: omschrijving en betalingskenmerk alleen als ze er zijn.
        assertThat(transacties.get(1).omschrijving()).contains("Salaris oktober");
        assertThat(transacties.get(1).betalingskenmerk()).contains("SAL-2026-10");
        assertThat(incasso.omschrijving()).isEmpty();
        assertThat(incasso.betalingskenmerk()).isEmpty();
    }

    private List<String> zoek(LocalDate vandaag, Zoekfilter filter) {
        return repository.vanRekening("jan", VAN_JAN, vandaag, filter, Optional.empty(), 50).orElseThrow().transacties()
                .stream().map(Transactie::tegenpartij).toList();
    }

    private static Zoekfilter filter(String tekst, String van, String tm, String type, String richting) {
        return Zoekfilter.van(Optional.ofNullable(tekst), Optional.ofNullable(van), Optional.ofNullable(tm),
                Optional.ofNullable(type), Optional.ofNullable(richting));
    }

    /** S5.1: een deel van de naam, hoofdletterongevoelig. */
    @Test
    void zoektOpEenDeelVanDeNaam() {
        assertThat(zoek(NA_ALLES, filter("KORST", null, null, null, null))).containsExactly("Bakkerij De Korst");
    }

    @Test
    void zoektOpHetIbanVanDeTegenpartijOokMetSpaties() {
        assertThat(zoek(NA_ALLES, filter("nl91 abna", null, null, null, null))).containsExactly("Woningstichting Thuis");
    }

    /** Het bereik geldt voor de absolute waarde, met de grenzen erbij. */
    @Test
    void zoektOpBedragInclusiefDeGrenzenOpAbsoluteWaarde() {
        assertThat(zoek(NA_ALLES, filter(null, "12,50", "812.40", null, null)))
                .containsExactly("Bakkerij De Korst", "Woningstichting Thuis");
    }

    @Test
    void zoektOpType() {
        assertThat(zoek(NA_ALLES, filter(null, null, null, "BETAALAUTOMAAT", null))).containsExactly("Bakkerij De Korst");
    }

    @Test
    void zoektOpRichting() {
        assertThat(zoek(NA_ALLES, filter(null, null, null, null, "inkomend"))).containsExactly("Werkgever B.V.");
        assertThat(zoek(NA_ALLES, filter(null, null, null, null, "uitgaand")))
                .containsExactly("Bakkerij De Korst", "Woningstichting Thuis");
    }

    /** Alle ingevulde velden moeten gelden (EN). */
    @Test
    void alleIngevuldeVeldenMoetenGelden() {
        assertThat(zoek(NA_ALLES, filter(null, "100", null, null, "uitgaand"))).containsExactly("Woningstichting Thuis");
    }

    /** ADR 0013: ook zoeken vindt niets na "vandaag". */
    @Test
    void zoekenVindtNietsNaVandaag() {
        assertThat(zoek(LocalDate.of(2026, 10, 1), filter(null, null, null, null, "uitgaand")))
                .containsExactly("Woningstichting Thuis");
    }

    /** "Toon meer" met een zoekopdracht: de volgende pagina blijft binnen het filter. */
    @Test
    void deVolgendePaginaBlijftBinnenHetFilter() {
        Zoekfilter uitgaand = filter(null, null, null, null, "uitgaand");
        Pagina eerste = repository.vanRekening("jan", VAN_JAN, NA_ALLES, uitgaand, Optional.empty(), 1).orElseThrow();
        Pagina tweede = repository.vanRekening("jan", VAN_JAN, NA_ALLES, uitgaand, eerste.volgende(), 1).orElseThrow();

        assertThat(eerste.transacties()).extracting(Transactie::tegenpartij).containsExactly("Bakkerij De Korst");
        assertThat(tweede.transacties()).extracting(Transactie::tegenpartij).containsExactly("Woningstichting Thuis");
        assertThat(tweede.volgende()).isEmpty();
    }

    /** Injectie (slice 5): %, _ en ' zijn gewone tekens; ze geven geen andere resultaten en geen fout. */
    @Test
    void jokertekensEnAanhalingstekensZijnGewoneTekens() {
        Iban vanLies = Iban.simBank(3L);
        jdbc.sql("INSERT INTO rekeninghouder (gebruikersnaam, naam) VALUES ('lies', 'Lies')").update();
        jdbc.sql("""
                INSERT INTO rekening (iban, rekeninghouder_id, soort, openingssaldo)
                SELECT :iban, id, 'betaal', 0.00 FROM rekeninghouder WHERE gebruikersnaam = 'lies'""")
                .param("iban", vanLies.toString()).update();
        boek(vanLies, "2026-10-01", "50% korting");
        boek(vanLies, "2026-10-02", "Bakker_Jansen");
        boek(vanLies, "2026-10-03", "Bakkerij 't Hoekje");

        assertThat(zoekBij("lies", vanLies, "%")).containsExactly("50% korting");
        // Als jokerteken zou "ker_j" ook "Bakkerij 't Hoekje" vinden.
        assertThat(zoekBij("lies", vanLies, "ker_j")).containsExactly("Bakker_Jansen");
        assertThat(zoekBij("lies", vanLies, "_")).containsExactly("Bakker_Jansen");
        assertThat(zoekBij("lies", vanLies, "'t")).containsExactly("Bakkerij 't Hoekje");
        assertThat(zoekBij("lies", vanLies, "\\")).isEmpty();
    }

    private List<String> zoekBij(String gebruikersnaam, Iban iban, String tekst) {
        return repository.vanRekening(gebruikersnaam, iban, NA_ALLES, filter(tekst, null, null, null, null),
                Optional.empty(), 50).orElseThrow().transacties().stream().map(Transactie::tegenpartij).toList();
    }

    /** Met offset zou de nieuwe boeking de lijst opschuiven en "Nieuwste" dubbel tonen; met keyset niet (ADR 0011). */
    @Test
    void eenNieuweBoekingTussenTweePaginasVerschuiftDeVolgendePaginaNiet() {
        Iban vanKees = Iban.simBank(2L);
        jdbc.sql("INSERT INTO rekeninghouder (gebruikersnaam, naam) VALUES ('kees', 'Kees')").update();
        jdbc.sql("""
                INSERT INTO rekening (iban, rekeninghouder_id, soort, openingssaldo)
                SELECT :iban, id, 'betaal', 0.00 FROM rekeninghouder WHERE gebruikersnaam = 'kees'""")
                .param("iban", vanKees.toString()).update();
        boek(vanKees, "2026-10-01", "Oudste");
        boek(vanKees, "2026-10-02", "Nieuwste");
        Pagina eerste = repository.vanRekening("kees", vanKees, NA_ALLES, Zoekfilter.GEEN, Optional.empty(), 1).orElseThrow();
        boek(vanKees, "2026-10-03", "Later geboekt");

        Pagina tweede = repository.vanRekening("kees", vanKees, NA_ALLES, Zoekfilter.GEEN, eerste.volgende(), 1).orElseThrow();

        assertThat(eerste.transacties()).extracting(Transactie::tegenpartij).containsExactly("Nieuwste");
        assertThat(tweede.transacties()).extracting(Transactie::tegenpartij).containsExactly("Oudste");
    }

    private static void boek(Iban iban, String datum, String tegenpartij) {
        jdbc.sql("""
                        INSERT INTO transactie (iban, datum, bedrag, tegenpartij, tijdstip)
                        VALUES (:iban, CAST(:datum AS date), -1.00, :tegenpartij, CAST(:datum AS date))""")
                .param("iban", iban.toString()).param("datum", datum).param("tegenpartij", tegenpartij).update();
    }

    @Test
    void eenPaginaMetPreciesAlleResterendeTransactiesHeeftGeenVolgende() {
        assertThat(repository.vanRekening("jan", VAN_JAN, NA_ALLES, Zoekfilter.GEEN, Optional.empty(), 3).orElseThrow().volgende()).isEmpty();
        assertThat(repository.vanRekening("jan", VAN_JAN, NA_ALLES, Zoekfilter.GEEN, Optional.empty(), 2).orElseThrow().volgende()).isPresent();
    }

    @Test
    void eenEigenRekeningZonderTransactiesGeeftEenLegePagina() {
        assertThat(repository.vanRekening("piet", VAN_PIET, NA_ALLES, Zoekfilter.GEEN, Optional.empty(), 50))
                .contains(new Pagina(List.of(), Optional.empty()));
    }

    @Test
    void eenRekeningVanEenAnderGeeftNiets() {
        assertThat(repository.vanRekening("piet", VAN_JAN, NA_ALLES, Zoekfilter.GEEN, Optional.empty(), 50)).isEmpty();
    }
}
