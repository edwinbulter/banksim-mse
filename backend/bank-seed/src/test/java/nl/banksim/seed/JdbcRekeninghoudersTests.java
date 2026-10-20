package nl.banksim.seed;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.Map;

import nl.banksim.domain.geld.Bedrag;
import nl.banksim.domain.rekening.Iban;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/** Tegen PostgreSQL met het schema dat bank-api bij het starten migreert (ADR 0009). */
@Testcontainers
class JdbcRekeninghoudersTests {

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(TestDatabase.IMAGE);

    static JdbcClient jdbc;

    @BeforeAll
    static void schema() {
        var dataSource = new DriverManagerDataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        Flyway.configure().dataSource(dataSource).locations(TestDatabase.MIGRATIES).load().migrate();
        jdbc = JdbcClient.create(dataSource);
    }

    private final JdbcRekeninghouders rekeninghouders = new JdbcRekeninghouders(jdbc);

    @Test
    void slaatDeRekeninghouderEnDeBetaalrekeningMetOpeningssaldoOp() {
        rekeninghouders.voegToeMetBetaalrekening("jan-a1-1", Iban.simBank(1L), Bedrag.van("1842.17"));

        Map<String, Object> rij = jdbc.sql("""
                        SELECT h.gebruikersnaam, r.iban, r.soort, r.openingssaldo
                        FROM rekening r JOIN rekeninghouder h ON h.id = r.rekeninghouder_id
                        WHERE h.gebruikersnaam = 'jan-a1-1'""")
                .query().singleRow();
        assertThat(rij).containsEntry("iban", "NL94SIMB0000000001")
                .containsEntry("soort", "betaal")
                .containsEntry("openingssaldo", new BigDecimal("1842.17"));
    }

    @Test
    void eenGebruikersnaamBestaatMaarEenKeer() {
        rekeninghouders.voegToeMetBetaalrekening("piet-a1-2", Iban.simBank(2L), Bedrag.van("1.00"));

        assertThatThrownBy(() -> rekeninghouders.voegToeMetBetaalrekening("piet-a1-2", Iban.simBank(3L), Bedrag.van("1.00")))
                .isInstanceOf(DuplicateKeyException.class);
    }
}
