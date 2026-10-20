package nl.banksim.api.rekening;

import static org.assertj.core.api.Assertions.assertThat;

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
class JdbcRekeningRepositoryTests {

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(DockerImageName
            .parse("postgres:18.6-alpine@sha256:77f585114c32fbca283dc835b0596f4e52b51b4c6662d7810b2f4084f60a1873")
            .asCompatibleSubstituteFor("postgres"));

    static JdbcClient jdbc;

    @BeforeAll
    static void schemaEnRekeningen() {
        var dataSource = new DriverManagerDataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        Flyway.configure().dataSource(dataSource).load().migrate();
        jdbc = JdbcClient.create(dataSource);
        jdbc.sql("INSERT INTO rekeninghouder (gebruikersnaam) VALUES ('jan'), ('piet')").update();
        jdbc.sql("""
                INSERT INTO rekening (iban, rekeninghouder_id, soort, openingssaldo)
                SELECT 'NL94SIMB0000000001', id, 'betaal', 1842.17 FROM rekeninghouder WHERE gebruikersnaam = 'jan'
                UNION ALL
                SELECT 'NL38SIMB0123456789', id, 'betaal', 5.00 FROM rekeninghouder WHERE gebruikersnaam = 'piet'
                """).update();
    }

    private final JdbcRekeningRepository repository = new JdbcRekeningRepository(jdbc);

    @Test
    void geeftAlleenDeRekeningenVanDeGebruikerMetOpeningssaldoAlsSaldo() {
        assertThat(repository.vanGebruiker("jan")).containsExactly(
                new Rekening(Soort.BETAAL, Iban.van("NL94SIMB0000000001"), Bedrag.van("1842.17")));
    }

    @Test
    void geeftNietsVoorEenOnbekendeGebruiker() {
        assertThat(repository.vanGebruiker("onbekend")).isEmpty();
    }
}
