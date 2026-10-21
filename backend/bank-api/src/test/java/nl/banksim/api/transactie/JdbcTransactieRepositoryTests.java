package nl.banksim.api.transactie;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;

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

    private final JdbcTransactieRepository repository = new JdbcTransactieRepository(jdbc);

    @Test
    void geeftDeTransactiesVanNieuwNaarOudEnOpDezelfdeDagDeLaatstGeboekteEerst() {
        assertThat(repository.vanRekening("jan", VAN_JAN)).contains(List.of(
                new Transactie(LocalDate.of(2026, 10, 2), "Bakkerij De Korst", Bedrag.van("-12.50")),
                new Transactie(LocalDate.of(2026, 10, 2), "Werkgever B.V.", Bedrag.van("3215.00")),
                new Transactie(LocalDate.of(2026, 10, 1), "Woningstichting Thuis", Bedrag.van("-812.40"))));
    }

    @Test
    void eenEigenRekeningZonderTransactiesGeeftEenLegeLijst() {
        assertThat(repository.vanRekening("piet", VAN_PIET)).contains(List.of());
    }

    @Test
    void eenRekeningVanEenAnderGeeftNiets() {
        assertThat(repository.vanRekening("piet", VAN_JAN)).isEmpty();
    }
}
