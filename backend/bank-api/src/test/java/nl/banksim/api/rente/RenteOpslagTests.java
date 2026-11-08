package nl.banksim.api.rente;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import nl.banksim.api.PostgresTestDatabase;
import nl.banksim.domain.geld.Bedrag;
import nl.banksim.domain.rekening.Iban;
import nl.banksim.domain.rente.DagSaldo;
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

/**
 * ADR 0028, tegen PostgreSQL met de migraties van bank-api: de dagsaldi waarover de rente gaat, en het markeren van een
 * afgesloten maand.
 */
@Testcontainers
class RenteOpslagTests {

    @Container
    static final PostgreSQLContainer POSTGRES = PostgresTestDatabase.container();

    static final Iban SPAAR_JAN = Iban.van("NL94SIMB0000000001");

    static RenteOpslag opslag;
    static TransactionTemplate transactie;

    /**
     * Jan opent met € 1.000,00. Vóór 1 oktober: rente en een inleg. In oktober: een inleg op de 1e, een opname op de
     * 3e en de rente van oktober op de 31e.
     */
    @BeforeAll
    static void spaarrekeningMetTransacties() {
        var dataSource = new DriverManagerDataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        Flyway.configure().dataSource(dataSource).load().migrate();
        JdbcClient jdbc = JdbcClient.create(dataSource);
        opslag = new RenteOpslag(jdbc);
        transactie = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
        jdbc.sql("INSERT INTO rekeninghouder (gebruikersnaam, naam) VALUES ('jan', 'Jan')").update();
        jdbc.sql("""
                INSERT INTO rekening (iban, rekeninghouder_id, soort, openingssaldo, geopend_op)
                SELECT 'NL94SIMB0000000001', id, 'spaar', 1000.00, '2026-01-01' FROM rekeninghouder WHERE gebruikersnaam = 'jan'
                """).update();
        jdbc.sql("""
                INSERT INTO transactie (iban, datum, bedrag, tegenpartij, type, tijdstip) VALUES
                ('NL94SIMB0000000001', '2026-09-30', 2.00, 'BankSim', 'RENTE', '2026-09-30T23:59+02:00'),
                ('NL94SIMB0000000001', '2026-09-30', 100.00, 'Jan', 'INLEG', '2026-09-30T12:00+02:00'),
                ('NL94SIMB0000000001', '2026-10-01', 50.00, 'Jan', 'INLEG', '2026-10-01T12:00+02:00'),
                ('NL94SIMB0000000001', '2026-10-03', -30.00, 'Jan', 'OPNAME', '2026-10-03T12:00+02:00'),
                ('NL94SIMB0000000001', '2026-10-31', 3.00, 'BankSim', 'RENTE', '2026-10-31T23:59+01:00')
                """).update();
    }

    /** Het saldo bij het begin van de eerste dag telt alles van daarvóór mee, ook de rente; die dag zelf niet. */
    @Test
    void beginnenBijHetSaldoAanHetBeginVanDeEersteDag() {
        assertThat(opslag.dagsaldi(SPAAR_JAN, LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 3))).containsExactly(
                new DagSaldo(LocalDate.of(2026, 10, 1), Bedrag.van("1152.00")),
                new DagSaldo(LocalDate.of(2026, 10, 2), Bedrag.van("1152.00")),
                new DagSaldo(LocalDate.of(2026, 10, 3), Bedrag.van("1122.00")));
    }

    /** ADR 0030: de rente van de maand zelf telt niet mee, anders verandert een herberekening haar eigen uitkomst. */
    @Test
    void deRenteVanDeMaandZelfTeltNietMee() {
        assertThat(opslag.dagsaldi(SPAAR_JAN, LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31)).getLast())
                .isEqualTo(new DagSaldo(LocalDate.of(2026, 10, 31), Bedrag.van("1122.00")));
    }

    /**
     * Backlog fase 4.3: de nachtelijke afsluiting en een PUT van de beheerder sluiten tegelijk dezelfde maand af. De
     * tweede wacht op de unieke sleutel van de eerste, en ziet daarna dat de maand al gemarkeerd is (ADR 0028).
     */
    @Test
    void tweeGelijktijdigeAfsluitingenMarkerenDeMaandEenKeer() throws Exception {
        YearMonth september = YearMonth.of(2026, 9);
        var eersteHeeftGemarkeerd = new CountDownLatch(1);
        var eersteMagStoppen = new CountDownLatch(1);
        CompletableFuture<Boolean> eerste = CompletableFuture.supplyAsync(() -> transactie.execute(status -> {
            boolean gemarkeerd = opslag.markeer(SPAAR_JAN, september);
            eersteHeeftGemarkeerd.countDown();
            wacht(eersteMagStoppen);
            return gemarkeerd;
        }));
        assertThat(eersteHeeftGemarkeerd.await(10, TimeUnit.SECONDS)).isTrue();

        CompletableFuture<Boolean> tweede = CompletableFuture.supplyAsync(() ->
                transactie.execute(status -> opslag.markeer(SPAAR_JAN, september)));
        Thread.sleep(500);
        assertThat(tweede).as("tweede wacht op de eerste").isNotDone();

        eersteMagStoppen.countDown();
        assertThat(eerste.get(10, TimeUnit.SECONDS)).isTrue();
        assertThat(tweede.get(10, TimeUnit.SECONDS)).isFalse();
    }

    private static void wacht(CountDownLatch latch) {
        try {
            latch.await(10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
