package nl.banksim.seed;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import nl.banksim.domain.geld.Bedrag;
import nl.banksim.domain.grootboek.Overboeking;
import nl.banksim.domain.grootboek.Partij;
import nl.banksim.domain.rekening.Iban;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/** ADR 0042: tegen PostgreSQL met het schema van bank-api, dus ook met de trigger die de overboeking controleert. */
@Testcontainers
class JdbcOverboekingenTests {

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(TestDatabase.IMAGE);

    static JdbcClient jdbc;
    static TransactionTemplate transactie;
    static Iban betaal = Iban.simBank(1L);
    static Iban spaar;

    @BeforeAll
    static void schemaEnRekeningen() {
        var dataSource = new DriverManagerDataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        Flyway.configure().dataSource(dataSource).locations(TestDatabase.MIGRATIES).load().migrate();
        jdbc = JdbcClient.create(dataSource);
        transactie = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
        new JdbcRekeninghouders(jdbc).voegToeMetBetaalrekening("jan", "Jan", betaal, Bedrag.van("1000.00"));
        spaar = new JdbcSpaarrekeningen(jdbc, () -> Iban.simBank(2L)).open("jan", Bedrag.van("0.00"), Optional.empty());
    }

    private static Overboeking inleg(int dag) {
        return Overboeking.inleg(new Partij(betaal, "Jan"), new Partij(spaar, "Jan"), Bedrag.van("400.00"),
                LocalDateTime.of(2021, 10, dag, 9, 30), "Maandelijks sparen");
    }

    @Test
    void eenInlegIsEenOverboekingMetTweeKanten() {
        new JdbcOverboekingen(jdbc, transactie).schrijfAlle(List.of(inleg(25), inleg(26)));

        assertThat(jdbc.sql("SELECT count(*) FROM overboeking WHERE type = 'OVERSCHRIJVING'").query(Long.class).single()).isEqualTo(2);
        List<Map<String, Object>> rijen = jdbc.sql("""
                        SELECT t.iban, t.bedrag, t.type, t.tegen_iban, t.tegenpartij, t.omschrijving, t.datum::text AS datum
                        FROM transactie t JOIN overboeking o ON o.id = t.overboeking_id
                        WHERE t.datum = '2021-10-25' ORDER BY t.bedrag""")
                .query().listOfRows();
        assertThat(rijen).hasSize(2);
        assertThat(rijen.getFirst()).containsEntry("iban", betaal.toString()).containsEntry("bedrag", new BigDecimal("-400.00"))
                .containsEntry("type", "OVERSCHRIJVING").containsEntry("tegen_iban", spaar.toString())
                .containsEntry("tegenpartij", "Jan").containsEntry("omschrijving", "Maandelijks sparen")
                .containsEntry("datum", "2021-10-25");
        assertThat(rijen.getLast()).containsEntry("iban", spaar.toString()).containsEntry("bedrag", new BigDecimal("400.00"))
                .containsEntry("type", "INLEG").containsEntry("tegen_iban", betaal.toString());
    }
}
