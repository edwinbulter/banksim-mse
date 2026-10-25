package nl.banksim.api.betaling;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

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
import org.testcontainers.utility.DockerImageName;

/** ADR 0027, tegen PostgreSQL met de migraties van bank-api: een sleutel per gebruiker, één keer vast te leggen. */
@Testcontainers
class JdbcIdempotentieTests {

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(DockerImageName
            .parse("postgres:18.6-alpine@sha256:77f585114c32fbca283dc835b0596f4e52b51b4c6662d7810b2f4084f60a1873")
            .asCompatibleSubstituteFor("postgres"));

    static JdbcClient jdbc;
    static TransactionTemplate transactie;

    @BeforeAll
    static void schema() {
        var dataSource = new DriverManagerDataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        Flyway.configure().dataSource(dataSource).load().migrate();
        jdbc = JdbcClient.create(dataSource);
        transactie = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
    }

    private final JdbcIdempotentie idempotentie = new JdbcIdempotentie(jdbc);

    @Test
    void eenNieuweSleutelIsLeegEnDaarnaGeeftHijDeHashVanHetEerdereVerzoek() {
        String sleutel = UUID.randomUUID().toString();

        assertThat(idempotentie.claim(sleutel, "jan", "hash-1")).isEmpty();
        assertThat(idempotentie.claim(sleutel, "jan", "hash-2")).contains("hash-1");
    }

    @Test
    void eenSleutelGeldtPerGebruiker() {
        String sleutel = UUID.randomUUID().toString();
        idempotentie.claim(sleutel, "jan", "hash-jan");

        assertThat(idempotentie.claim(sleutel, "piet", "hash-piet")).isEmpty();
    }

    /** Een gelijktijdige tweede poging wacht tot de eerste klaar is, en ziet dan haar hash. */
    @Test
    void eenGelijktijdigeTweedePogingWachtOpDeEersteEnZietHaarHash() throws Exception {
        String sleutel = UUID.randomUUID().toString();
        var eersteHeeftGeclaimd = new CountDownLatch(1);
        var eersteMagStoppen = new CountDownLatch(1);
        CompletableFuture<Void> eerste = CompletableFuture.runAsync(() -> transactie.executeWithoutResult(status -> {
            idempotentie.claim(sleutel, "jan", "hash-1");
            eersteHeeftGeclaimd.countDown();
            wacht(eersteMagStoppen);
        }));
        assertThat(eersteHeeftGeclaimd.await(10, TimeUnit.SECONDS)).isTrue();

        CompletableFuture<Optional<String>> tweede = CompletableFuture.supplyAsync(() ->
                transactie.execute(status -> idempotentie.claim(sleutel, "jan", "hash-1")));
        Thread.sleep(500);
        assertThat(tweede).as("tweede wacht op de eerste").isNotDone();

        eersteMagStoppen.countDown();
        eerste.get(10, TimeUnit.SECONDS);
        assertThat(tweede.get(10, TimeUnit.SECONDS)).contains("hash-1");
    }

    /** Een geweigerde eerste poging rolt terug, ook de sleutel: een nieuwe poging wordt opnieuw beoordeeld. */
    @Test
    void naEenTeruggeroldeEerstePogingIsDeSleutelWeerNieuw() {
        String sleutel = UUID.randomUUID().toString();
        transactie.executeWithoutResult(status -> {
            idempotentie.claim(sleutel, "jan", "hash-1");
            status.setRollbackOnly();
        });

        assertThat(idempotentie.claim(sleutel, "jan", "hash-1")).isEmpty();
    }

    private static void wacht(CountDownLatch latch) {
        try {
            latch.await(10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
