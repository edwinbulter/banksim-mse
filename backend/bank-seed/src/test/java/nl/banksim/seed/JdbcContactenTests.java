package nl.banksim.seed;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;

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

/** S7.1: de contactenlijst van de bank, tegen PostgreSQL met het schema van bank-api (ADR 0009). */
@Testcontainers
class JdbcContactenTests {

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(TestDatabase.IMAGE);

    static JdbcClient jdbc;

    @BeforeAll
    static void schema() {
        var dataSource = new DriverManagerDataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        Flyway.configure().dataSource(dataSource).locations(TestDatabase.MIGRATIES).load().migrate();
        jdbc = JdbcClient.create(dataSource);
    }

    private final JdbcContacten contacten = new JdbcContacten(jdbc);

    @Test
    void slaatHetContactMetIbanEnNaamOp() {
        contacten.voegToe(Iban.simBank(1L), "Sanne");

        Map<String, Object> rij = jdbc.sql("SELECT iban, naam FROM contact WHERE iban = 'NL94SIMB0000000001'")
                .query().singleRow();
        assertThat(rij).containsEntry("naam", "Sanne");
    }

    @Test
    void eenIbanStaatMaarEenKeerInDeContactenlijst() {
        contacten.voegToe(Iban.simBank(2L), "Piet");

        assertThatThrownBy(() -> contacten.voegToe(Iban.simBank(2L), "Ander"))
                .isInstanceOf(DuplicateKeyException.class);
    }
}
