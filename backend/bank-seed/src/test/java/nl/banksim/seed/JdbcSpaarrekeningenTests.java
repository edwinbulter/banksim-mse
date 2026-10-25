package nl.banksim.seed;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;
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

/** S8.2: een spaarrekening bij een bestaande rekeninghouder, tegen PostgreSQL met het schema van bank-api. */
@Testcontainers
class JdbcSpaarrekeningenTests {

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(TestDatabase.IMAGE);

    static JdbcClient jdbc;

    @BeforeAll
    static void schemaEnJan() {
        var dataSource = new DriverManagerDataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        Flyway.configure().dataSource(dataSource).locations(TestDatabase.MIGRATIES).load().migrate();
        jdbc = JdbcClient.create(dataSource);
        jdbc.sql("INSERT INTO rekeninghouder (gebruikersnaam, naam) VALUES ('jan-a1-1', 'Jan')").update();
    }

    @Test
    void opentEenSpaarrekeningMetBeginsaldoBijDeRekeninghouder() {
        Iban iban = new JdbcSpaarrekeningen(jdbc, () -> Iban.simBank(7L)).open("jan-a1-1", Bedrag.van("12405.63"), Optional.empty());

        assertThat(iban).isEqualTo(Iban.simBank(7L));
        Map<String, Object> rij = jdbc.sql("""
                        SELECT h.gebruikersnaam, r.soort, r.openingssaldo
                        FROM rekening r JOIN rekeninghouder h ON h.id = r.rekeninghouder_id WHERE r.iban = :iban""")
                .param("iban", iban.toString()).query().singleRow();
        assertThat(rij).containsEntry("gebruikersnaam", "jan-a1-1")
                .containsEntry("soort", "spaar")
                .containsEntry("openingssaldo", new BigDecimal("12405.63"));
    }

    /** S8.1: de rente begint op de dag van openen. */
    @Test
    void bewaartDeDatumVanOpenen() {
        Iban iban = new JdbcSpaarrekeningen(jdbc, () -> Iban.simBank(9L))
                .open("jan-a1-1", Bedrag.van("10000.00"), Optional.of(LocalDate.of(2026, 1, 1)));

        assertThat(jdbc.sql("SELECT geopend_op FROM rekening WHERE iban = :iban").param("iban", iban.toString())
                .query(LocalDate.class).single()).isEqualTo(LocalDate.of(2026, 1, 1));
    }

    @Test
    void zonderRekeninghouderGeeftHetEenFout() {
        assertThatThrownBy(() -> new JdbcSpaarrekeningen(jdbc, () -> Iban.simBank(8L)).open("bestaat-niet", Bedrag.van("1.00"), Optional.empty()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("bestaat-niet");
    }
}
