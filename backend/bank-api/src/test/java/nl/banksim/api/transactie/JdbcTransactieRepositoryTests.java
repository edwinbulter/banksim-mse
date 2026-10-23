package nl.banksim.api.transactie;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import nl.banksim.domain.geld.Bedrag;
import nl.banksim.domain.rekening.Iban;
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
        jdbc.sql("INSERT INTO rekeninghouder (gebruikersnaam) VALUES ('jan'), ('piet')").update();
        jdbc.sql("""
                INSERT INTO rekening (iban, rekeninghouder_id, soort, openingssaldo)
                SELECT 'NL94SIMB0000000001', id, 'betaal', 1000.00 FROM rekeninghouder WHERE gebruikersnaam = 'jan'
                UNION ALL
                SELECT 'NL38SIMB0123456789', id, 'betaal', 5.00 FROM rekeninghouder WHERE gebruikersnaam = 'piet'
                """).update();
        // Bewust niet in datumvolgorde geboekt; twee transacties op 2 oktober.
        jdbc.sql("""
                INSERT INTO transactie (iban, datum, bedrag, tegenpartij) VALUES
                ('NL94SIMB0000000001', '2026-10-02', 3215.00, 'Werkgever B.V.'),
                ('NL94SIMB0000000001', '2026-10-01', -812.40, 'Woningstichting Thuis'),
                ('NL94SIMB0000000001', '2026-10-02', -12.50, 'Bakkerij De Korst')
                """).update();
    }

    /** Na alle transacties in deze test, zodat de tests niet afhangen van de dag waarop ze draaien. */
    static final LocalDate NA_ALLES = LocalDate.of(2026, 12, 31);

    private final JdbcTransactieRepository repository = new JdbcTransactieRepository(jdbc);

    /** ADR 0013: transacties na "vandaag" zijn onzichtbaar, ook voor de cursor; een transactie óp vandaag niet. */
    @Test
    void geeftAlleenDeTransactiesTotEnMetVandaag() {
        Pagina pagina = repository.vanRekening("jan", VAN_JAN, LocalDate.of(2026, 10, 1), Optional.empty(), 1)
                .orElseThrow();

        assertThat(pagina.transacties()).extracting(Transactie::tegenpartij).containsExactly("Woningstichting Thuis");
        assertThat(pagina.volgende()).isEmpty();
    }

    @Test
    void geeftDeTransactiesVanNieuwNaarOudEnOpDezelfdeDagDeLaatstGeboekteEerst() {
        assertThat(repository.vanRekening("jan", VAN_JAN, NA_ALLES, Optional.empty(), 50).map(Pagina::transacties)).contains(List.of(
                new Transactie(LocalDate.of(2026, 10, 2), "Bakkerij De Korst", Bedrag.van("-12.50")),
                new Transactie(LocalDate.of(2026, 10, 2), "Werkgever B.V.", Bedrag.van("3215.00")),
                new Transactie(LocalDate.of(2026, 10, 1), "Woningstichting Thuis", Bedrag.van("-812.40"))));
    }

    /** De grens valt midden in 2 oktober: de volgende pagina begint bij de transactie ná de laatste getoonde. */
    @Test
    void dePaginaNaDeLaatsteGaatVerderBinnenDezelfdeDag() {
        Pagina eerste = repository.vanRekening("jan", VAN_JAN, NA_ALLES, Optional.empty(), 1).orElseThrow();
        Pagina tweede = repository.vanRekening("jan", VAN_JAN, NA_ALLES, eerste.volgende(), 1).orElseThrow();
        Pagina derde = repository.vanRekening("jan", VAN_JAN, NA_ALLES, tweede.volgende(), 1).orElseThrow();

        assertThat(List.of(eerste, tweede, derde)).extracting(pagina -> pagina.transacties().getFirst().tegenpartij())
                .containsExactly("Bakkerij De Korst", "Werkgever B.V.", "Woningstichting Thuis");
        assertThat(eerste.volgende()).isPresent();
        assertThat(tweede.volgende()).isPresent();
        assertThat(derde.volgende()).isEmpty();
    }

    /** Met offset zou de nieuwe boeking de lijst opschuiven en "Nieuwste" dubbel tonen; met keyset niet (ADR 0011). */
    @Test
    void eenNieuweBoekingTussenTweePaginasVerschuiftDeVolgendePaginaNiet() {
        Iban vanKees = Iban.simBank(2L);
        jdbc.sql("INSERT INTO rekeninghouder (gebruikersnaam) VALUES ('kees')").update();
        jdbc.sql("""
                INSERT INTO rekening (iban, rekeninghouder_id, soort, openingssaldo)
                SELECT :iban, id, 'betaal', 0.00 FROM rekeninghouder WHERE gebruikersnaam = 'kees'""")
                .param("iban", vanKees.toString()).update();
        boek(vanKees, "2026-10-01", "Oudste");
        boek(vanKees, "2026-10-02", "Nieuwste");
        Pagina eerste = repository.vanRekening("kees", vanKees, NA_ALLES, Optional.empty(), 1).orElseThrow();
        boek(vanKees, "2026-10-03", "Later geboekt");

        Pagina tweede = repository.vanRekening("kees", vanKees, NA_ALLES, eerste.volgende(), 1).orElseThrow();

        assertThat(eerste.transacties()).extracting(Transactie::tegenpartij).containsExactly("Nieuwste");
        assertThat(tweede.transacties()).extracting(Transactie::tegenpartij).containsExactly("Oudste");
    }

    private static void boek(Iban iban, String datum, String tegenpartij) {
        jdbc.sql("INSERT INTO transactie (iban, datum, bedrag, tegenpartij) VALUES (:iban, CAST(:datum AS date), -1.00, :tegenpartij)")
                .param("iban", iban.toString()).param("datum", datum).param("tegenpartij", tegenpartij).update();
    }

    @Test
    void eenPaginaMetPreciesAlleResterendeTransactiesHeeftGeenVolgende() {
        assertThat(repository.vanRekening("jan", VAN_JAN, NA_ALLES, Optional.empty(), 3).orElseThrow().volgende()).isEmpty();
        assertThat(repository.vanRekening("jan", VAN_JAN, NA_ALLES, Optional.empty(), 2).orElseThrow().volgende()).isPresent();
    }

    @Test
    void eenEigenRekeningZonderTransactiesGeeftEenLegePagina() {
        assertThat(repository.vanRekening("piet", VAN_PIET, NA_ALLES, Optional.empty(), 50))
                .contains(new Pagina(List.of(), Optional.empty()));
    }

    @Test
    void eenRekeningVanEenAnderGeeftNiets() {
        assertThat(repository.vanRekening("piet", VAN_JAN, NA_ALLES, Optional.empty(), 50)).isEmpty();
    }
}
