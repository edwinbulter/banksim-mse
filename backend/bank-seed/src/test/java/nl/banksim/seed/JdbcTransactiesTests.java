package nl.banksim.seed;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.sql.Date;
import java.time.LocalDate;
import java.util.Map;

import nl.banksim.domain.geld.Bedrag;
import nl.banksim.domain.rekening.Iban;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/** Tegen PostgreSQL met het schema dat bank-api bij het starten migreert (ADR 0009). */
@Testcontainers
class JdbcTransactiesTests {

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(TestDatabase.IMAGE);

    static JdbcClient jdbc;

    @BeforeAll
    static void schemaEnRekening() {
        var dataSource = new DriverManagerDataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        Flyway.configure().dataSource(dataSource).locations(TestDatabase.MIGRATIES).load().migrate();
        jdbc = JdbcClient.create(dataSource);
        new JdbcRekeninghouders(jdbc).voegToeMetBetaalrekening("jan-a1-1", Iban.simBank(1L), Bedrag.van("1000.00"));
    }

    private final JdbcTransacties transacties = new JdbcTransacties(jdbc);

    @Test
    void slaatDeTransactieOpBijDeRekening() {
        transacties.boek(Iban.simBank(1L), LocalDate.of(2026, 10, 1), Bedrag.van("-812.40"), "Woningstichting Thuis");

        Map<String, Object> rij = jdbc.sql("SELECT iban, datum, bedrag, tegenpartij FROM transactie WHERE tegenpartij = 'Woningstichting Thuis'")
                .query().singleRow();
        assertThat(rij).containsEntry("iban", "NL94SIMB0000000001")
                .containsEntry("datum", Date.valueOf("2026-10-01"))
                .containsEntry("bedrag", new BigDecimal("-812.40"));
    }

    @Test
    void weigertEenTransactieOpEenOnbekendeRekening() {
        assertThatThrownBy(() -> transacties.boek(Iban.simBank(2L), LocalDate.of(2026, 10, 1), Bedrag.van("1.00"), "Iemand"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
