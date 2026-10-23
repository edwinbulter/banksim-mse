package nl.banksim.seed;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.sql.Date;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Optional;

import nl.banksim.domain.geld.Bedrag;
import nl.banksim.domain.rekening.Iban;
import nl.banksim.domain.transactie.TransactieType;
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
        new JdbcRekeninghouders(jdbc).voegToeMetBetaalrekening("jan-a1-1", "Jan", Iban.simBank(1L), Bedrag.van("1000.00"));
    }

    private final JdbcTransacties transacties = new JdbcTransacties(jdbc);

    @Test
    void slaatDeTransactieOpBijDeRekening() {
        transacties.boek(Iban.simBank(1L), Boeking.opDatum(LocalDate.of(2026, 10, 1), Bedrag.van("-812.40"), "Woningstichting Thuis"));

        Map<String, Object> rij = jdbc.sql("SELECT iban, datum, bedrag, tegenpartij FROM transactie WHERE tegenpartij = 'Woningstichting Thuis'")
                .query().singleRow();
        assertThat(rij).containsEntry("iban", "NL94SIMB0000000001")
                .containsEntry("datum", Date.valueOf("2026-10-01"))
                .containsEntry("bedrag", new BigDecimal("-812.40"));
    }

    /** S4.1: type, tijdstip en tegen-IBAN; de datum is de dag in Europe/Amsterdam, ook vlak na middernacht. */
    @Test
    void slaatTypeTijdstipEnTegenIbanOpMetDeDatumInAmsterdam() {
        transacties.boek(Iban.simBank(1L), new Boeking(LocalDateTime.of(2026, 10, 1, 0, 30), Bedrag.van("-812.40"),
                "Incasso Nacht", TransactieType.INCASSO, Optional.of(Iban.van("NL91ABNA0417164300"))));

        Map<String, Object> rij = jdbc.sql("""
                        SELECT datum, type, tijdstip, tegen_iban FROM transactie WHERE tegenpartij = 'Incasso Nacht'""")
                .query().singleRow();
        assertThat(rij).containsEntry("datum", Date.valueOf("2026-10-01"))
                .containsEntry("type", "INCASSO")
                .containsEntry("tegen_iban", "NL91ABNA0417164300");
        assertThat(((OffsetDateTime) jdbc.sql("SELECT tijdstip FROM transactie WHERE tegenpartij = 'Incasso Nacht'")
                .query(OffsetDateTime.class).single()).toInstant()).isEqualTo(Instant.parse("2026-09-30T22:30:00Z"));
    }

    @Test
    void zonderTegenIbanBlijftDieLeeg() {
        transacties.boek(Iban.simBank(1L), new Boeking(LocalDateTime.of(2026, 10, 3, 13, 26), Bedrag.van("-63.48"),
                "Albert Heijn", TransactieType.BETAALAUTOMAAT, Optional.empty()));

        Map<String, Object> rij = jdbc.sql("SELECT type, tegen_iban FROM transactie WHERE tegenpartij = 'Albert Heijn'")
                .query().singleRow();
        assertThat(rij).containsEntry("type", "BETAALAUTOMAAT").containsEntry("tegen_iban", null);
    }

    @Test
    void weigertEenTransactieOpEenOnbekendeRekening() {
        assertThatThrownBy(() -> transacties.boek(Iban.simBank(2L), Boeking.opDatum(LocalDate.of(2026, 10, 1), Bedrag.van("1.00"), "Iemand")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
