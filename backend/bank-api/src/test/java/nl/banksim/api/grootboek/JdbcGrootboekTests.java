package nl.banksim.api.grootboek;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import nl.banksim.domain.geld.Bedrag;
import nl.banksim.domain.grootboek.Overboeking;
import nl.banksim.domain.grootboek.Partij;
import nl.banksim.domain.grootboek.SaldoVerloop;
import nl.banksim.domain.rekening.Iban;
import nl.banksim.domain.transactie.TransactieType;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.TransactionSystemException;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/** ADR 0024, tegen PostgreSQL met de migraties van bank-api: één overboeking, twee transacties, som 0. */
@Testcontainers
class JdbcGrootboekTests {

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(DockerImageName
            .parse("postgres:18.6-alpine@sha256:77f585114c32fbca283dc835b0596f4e52b51b4c6662d7810b2f4084f60a1873")
            .asCompatibleSubstituteFor("postgres"));

    static final Iban JAN = Iban.van("NL94SIMB0000000001");
    static final Iban SANNE = Iban.van("NL67SIMB0000000002");
    static final Iban PIET = Iban.van("NL40SIMB0000000003");
    static final Iban KEES = Iban.van("NL13SIMB0000000004");

    static JdbcClient jdbc;
    static TransactionTemplate transactie;

    @BeforeAll
    static void schemaEnRekeningen() {
        var dataSource = new DriverManagerDataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        Flyway.configure().dataSource(dataSource).load().migrate();
        jdbc = JdbcClient.create(dataSource);
        transactie = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
        jdbc.sql("INSERT INTO rekeninghouder (gebruikersnaam, naam) VALUES ('jan', 'Jan'), ('sanne', 'Sanne'), ('piet', 'Piet')")
                .update();
        jdbc.sql("""
                INSERT INTO rekening (iban, rekeninghouder_id, soort, openingssaldo)
                SELECT 'NL94SIMB0000000001', id, 'betaal', 500.00 FROM rekeninghouder WHERE gebruikersnaam = 'jan'
                UNION ALL
                SELECT 'NL67SIMB0000000002', id, 'betaal', 0.00 FROM rekeninghouder WHERE gebruikersnaam = 'sanne'
                UNION ALL
                SELECT 'NL40SIMB0000000003', id, 'betaal', 0.00 FROM rekeninghouder WHERE gebruikersnaam = 'piet'
                """).update();
        // Alleen voor het saldo: een eigen rekening, zodat de andere tests het niet beïnvloeden.
        jdbc.sql("INSERT INTO rekeninghouder (gebruikersnaam, naam) VALUES ('kees', 'Kees')").update();
        jdbc.sql("""
                INSERT INTO rekening (iban, rekeninghouder_id, soort, openingssaldo)
                SELECT 'NL13SIMB0000000004', id, 'betaal', 500.00 FROM rekeninghouder WHERE gebruikersnaam = 'kees'
                """).update();
        jdbc.sql("""
                INSERT INTO transactie (iban, datum, bedrag, tegenpartij, type, tijdstip) VALUES
                ('NL13SIMB0000000004', '2026-10-01', -100.00, 'Woningstichting', 'INCASSO', '2026-10-01T06:00+02:00'),
                ('NL13SIMB0000000004', '2026-10-05', -20.00, 'Bakker', 'BETAALAUTOMAAT', '2026-10-05T09:00+02:00'),
                ('NL13SIMB0000000004', '2026-10-10', 50.00, 'Werkgever', 'OVERSCHRIJVING', '2026-10-10T09:00+02:00'),
                ('NL13SIMB0000000004', '2026-10-10', -10.00, 'Kapper', 'BETAALAUTOMAAT', '2026-10-10T15:00+02:00')
                """).update();
    }

    private final JdbcGrootboek grootboek = new JdbcGrootboek(jdbc);

    @Test
    void geeftDeNaamBijDeEigenRekeningEnNietsBijDieVanEenAnder() {
        assertThat(grootboek.naamBijEigenRekening("jan", JAN)).contains("Jan");
        assertThat(grootboek.naamBijEigenRekening("jan", SANNE)).isEmpty();
        assertThat(grootboek.naamBijEigenRekening("jan", Iban.simBank(99L))).isEmpty();
    }

    /**
     * Openingssaldo plus de transacties tot en met de datum; daarna de bewegingen per latere dag opgeteld, in
     * datumvolgorde (ADR 0025).
     */
    @Test
    void hetVerloopIsHetSaldoOpDeDatumEnDaarnaDeBewegingenPerDag() {
        assertThat(grootboek.saldoVerloop(KEES, LocalDate.of(2026, 9, 30))).isEqualTo(new SaldoVerloop(Bedrag.van("500.00"),
                List.of(Bedrag.van("-100.00"), Bedrag.van("-20.00"), Bedrag.van("40.00"))));
        assertThat(grootboek.saldoVerloop(KEES, LocalDate.of(2026, 10, 5)))
                .isEqualTo(new SaldoVerloop(Bedrag.van("380.00"), List.of(Bedrag.van("40.00"))));
        assertThat(grootboek.saldoVerloop(KEES, LocalDate.of(2026, 10, 10)))
                .isEqualTo(new SaldoVerloop(Bedrag.van("420.00"), List.of()));
    }

    /** Beide kanten, met type, tijdstip in Europe/Amsterdam, uitvoerdatum en de andere kant als tegenpartij (S4.1). */
    @Test
    void schrijftDeOverboekingMetBeideTransacties() {
        var overboeking = new Overboeking(new Partij(JAN, "Jan"), new Partij(SANNE, "Sanne"), Bedrag.van("25.00"),
                TransactieType.ONLINE_BANKIEREN, LocalDateTime.of(2026, 10, 5, 14, 0));

        transactie.executeWithoutResult(status -> grootboek.schrijf(overboeking));

        List<Map<String, Object>> rijen = jdbc.sql("""
                        SELECT iban, bedrag, tegenpartij, tegen_iban, type, datum, tijdstip, overboeking_id
                        FROM transactie WHERE iban IN ('NL94SIMB0000000001', 'NL67SIMB0000000002') ORDER BY bedrag""")
                .query().listOfRows();
        assertThat(rijen).hasSize(2);
        assertThat(rijen.get(0)).containsEntry("iban", JAN.toString())
                .containsEntry("bedrag", new BigDecimal("-25.00"))
                .containsEntry("tegenpartij", "Sanne")
                .containsEntry("tegen_iban", SANNE.toString())
                .containsEntry("type", "ONLINE_BANKIEREN")
                .containsEntry("datum", java.sql.Date.valueOf(LocalDate.of(2026, 10, 5)));
        assertThat(rijen.get(1)).containsEntry("iban", SANNE.toString())
                .containsEntry("bedrag", new BigDecimal("25.00"))
                .containsEntry("tegenpartij", "Jan")
                .containsEntry("tegen_iban", JAN.toString());
        assertThat(rijen.get(0).get("overboeking_id")).isNotNull().isEqualTo(rijen.get(1).get("overboeking_id"));
        OffsetDateTime tijdstip = jdbc.sql("SELECT tijdstip FROM transactie WHERE iban = 'NL94SIMB0000000001'")
                .query(OffsetDateTime.class).single();
        assertThat(tijdstip.toInstant()).isEqualTo(OffsetDateTime.parse("2026-10-05T14:00+02:00").toInstant());
    }

    /** ADR 0026: een tweede transactie op dezelfde rekening wacht tot de eerste klaar is. */
    @Test
    void vergrendelHoudtEenTweedeTransactieOpDezelfdeRekeningTegenTotDeEersteKlaarIs() throws Exception {
        var eersteHeeftDeRekening = new CountDownLatch(1);
        var eersteMagStoppen = new CountDownLatch(1);
        CompletableFuture<Void> eerste = CompletableFuture.runAsync(() -> transactie.executeWithoutResult(status -> {
            grootboek.vergrendel(PIET, SANNE);
            eersteHeeftDeRekening.countDown();
            wacht(eersteMagStoppen);
        }));
        assertThat(eersteHeeftDeRekening.await(10, TimeUnit.SECONDS)).isTrue();

        CompletableFuture<Void> tweede = CompletableFuture.runAsync(() ->
                transactie.executeWithoutResult(status -> grootboek.vergrendel(PIET)));
        Thread.sleep(500);
        assertThat(tweede).as("tweede wacht op de eerste").isNotDone();

        eersteMagStoppen.countDown();
        eerste.get(10, TimeUnit.SECONDS);
        tweede.get(10, TimeUnit.SECONDS);
    }

    private static void wacht(CountDownLatch latch) {
        try {
            latch.await(10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /** De trigger is de laatste verdedigingslinie: ook SQL buiten LedgerService om komt er niet door. */
    @Test
    void eenOverboekingMetMaarEenTransactieFaaltBijCommit() {
        assertThatThrownBy(() -> transactie.executeWithoutResult(status -> {
            long id = nieuweOverboeking();
            transactieRij(id, PIET, "-1.00");
        })).isInstanceOf(TransactionSystemException.class).rootCause().hasMessageContaining("niet in balans");
    }

    /** Twee rekeningen en som 0, maar drie transacties: precies twee kanten, niet meer. */
    @Test
    void eenOverboekingMetDrieTransactiesFaaltBijCommit() {
        assertThatThrownBy(() -> transactie.executeWithoutResult(status -> {
            long id = nieuweOverboeking();
            transactieRij(id, PIET, "-2.00");
            transactieRij(id, SANNE, "1.00");
            transactieRij(id, SANNE, "1.00");
        })).isInstanceOf(TransactionSystemException.class).rootCause().hasMessageContaining("niet in balans");
    }

    @Test
    void eenOverboekingDieNietNulIsFaaltBijCommit() {
        assertThatThrownBy(() -> transactie.executeWithoutResult(status -> {
            long id = nieuweOverboeking();
            transactieRij(id, PIET, "-1.00");
            transactieRij(id, SANNE, "2.00");
        })).isInstanceOf(TransactionSystemException.class).rootCause().hasMessageContaining("niet in balans");
    }

    @Test
    void eenOverboekingOpDezelfdeRekeningFaaltBijCommit() {
        assertThatThrownBy(() -> transactie.executeWithoutResult(status -> {
            long id = nieuweOverboeking();
            transactieRij(id, PIET, "-1.00");
            transactieRij(id, PIET, "1.00");
        })).isInstanceOf(TransactionSystemException.class).rootCause().hasMessageContaining("niet in balans");
    }

    /** Bewegingen met de buitenwereld (seed, later de generator) blijven eenzijdig, zonder overboeking. */
    @Test
    void eenTransactieZonderOverboekingBlijftToegestaan() {
        transactieRij(null, PIET, "10.00");
    }

    private static long nieuweOverboeking() {
        return jdbc.sql("""
                        INSERT INTO overboeking (type, tijdstip, datum)
                        VALUES ('ONLINE_BANKIEREN', '2026-10-05T14:00+02:00', '2026-10-05') RETURNING id""")
                .query(Long.class).single();
    }

    private static void transactieRij(Long overboeking, Iban iban, String bedrag) {
        jdbc.sql("""
                        INSERT INTO transactie (iban, datum, bedrag, tegenpartij, type, tijdstip, overboeking_id)
                        VALUES (:iban, '2026-10-05', :bedrag, 'Iemand', 'ONLINE_BANKIEREN', '2026-10-05T14:00+02:00',
                                :overboeking)""")
                .param("iban", iban.toString())
                .param("bedrag", new BigDecimal(bedrag))
                .param("overboeking", overboeking)
                .update();
    }
}
