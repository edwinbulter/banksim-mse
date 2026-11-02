package nl.banksim.api.rente;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import nl.banksim.api.PostgresTestDatabase;
import nl.banksim.api.grootboek.LedgerService;
import nl.banksim.api.grootboek.OverboekingGeboekt;
import nl.banksim.domain.geld.Bedrag;
import nl.banksim.domain.grootboek.Overboeking;
import nl.banksim.domain.grootboek.Partij;
import nl.banksim.domain.rekening.Iban;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.event.EventListener;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Een opname op een spaarrekening botst met de maandafsluiting van diezelfde rekening. De botsing is geen toeval: de
 * {@link Sluis} houdt de opname vast zodra die de spaarrekening heeft vergrendeld, tot de afsluiting op een lock wacht
 * of klaar is. De uitkomst moet gelijk zijn aan die van dezelfde opname zonder botsing, eerst de opname en dan de
 * afsluiting. Een spaarrekening die na de renterekening sorteert, is het geval met het deadlockrisico.
 */
@SpringBootTest(properties = {
        "banksim.cursor.sleutel=test-sleutel-van-minstens-32-tekens",
        "management.server.port="})
@Import({PostgresTestDatabase.class, GelijktijdigeRenteTests.Opstelling.class})
class GelijktijdigeRenteTests {

    static final LocalDate EIND_JANUARI = LocalDate.of(2026, 1, 31);
    static final LocalDate EIND_FEBRUARI = LocalDate.of(2026, 2, 28);

    @Autowired
    RenteService rente;

    @Autowired
    LedgerService ledger;

    @Autowired
    JdbcClient jdbc;

    @Autowired
    Sluis sluis;

    @Autowired
    MaandRente maandRente;

    @Autowired
    RenteOpslag opslag;

    @Autowired
    TransactionTemplate transacties;

    @MockitoBean
    JwtDecoder jwtDecoder;

    /** De opname in januari vraagt een rentecorrectie, dus de renterekening, terwijl de afsluiting februari boekt. */
    @Test
    void eenOpnameMetRentecorrectieEnEenAfsluitingLopenNietVast() throws Exception {
        List<String> zonderBotsing = zonderBotsing(3100L, LocalDateTime.of(2026, 1, 15, 10, 0));

        List<String> metBotsing = metBotsing(3102L, LocalDateTime.of(2026, 1, 15, 10, 0));

        assertThat(metBotsing).filteredOn(mutatie -> mutatie.contains("Rentecorrectie januari")).hasSize(1);
        assertThat(metBotsing).filteredOn(mutatie -> mutatie.contains("Rente februari")).hasSize(1);
        assertThat(metBotsing).isEqualTo(zonderBotsing);
    }

    /** De opname valt in de maand die wordt afgesloten: de rente van februari moet hem meetellen. */
    @Test
    void eenAfsluitingTijdensEenOpnameInDieMaandTeltDeOpnameMee() throws Exception {
        List<String> zonderBotsing = zonderBotsing(3104L, LocalDateTime.of(2026, 2, 15, 10, 0));

        List<String> metBotsing = metBotsing(3106L, LocalDateTime.of(2026, 2, 15, 10, 0));

        assertThat(metBotsing).isEqualTo(zonderBotsing);
    }

    /**
     * Andersom: de afsluiting heeft de maand vastgelegd en daarmee, via de foreign key, een lichte lock op de
     * spaarrekening. De opname wacht, en ziet daarna dat februari is bijgeschreven: rente plus correctie is samen de rente
     * zonder botsing. December 2025 ligt vóór het openen en doet niets voor de rente; hij geeft hier alleen de echte lock.
     */
    @Test
    void eenOpnameTijdensEenAfsluitingCorrigeertDeRenteVanDieMaand() throws Exception {
        LocalDateTime tijdstip = LocalDateTime.of(2026, 2, 15, 10, 0);
        Rekeningen zonderBotsing = rekeningenMetJanuariBijgeschreven(3112L);
        ledger.boek(zonderBotsing.opname(tijdstip));
        rente.sluitAf(EIND_FEBRUARI);

        Rekeningen rekeningen = rekeningenMetJanuariBijgeschreven(3110L);
        assertThat(rekeningen.spaar().toString()).as("sorteert na de renterekening").isGreaterThan(MaandRente.BANKSIM.iban().toString());
        Spaarrekening spaarrekening = new Spaarrekening(rekeningen.spaar(), "botsing-3110", LocalDate.of(2026, 1, 1));

        var fouten = new ConcurrentLinkedQueue<Throwable>();
        var vastgelegd = new CountDownLatch(1);
        var opnameWacht = new CountDownLatch(1);
        var opnameKlaar = new CountDownLatch(1);
        ExecutorService draden = Executors.newFixedThreadPool(2);
        draden.execute(() -> {
            try {
                transacties.executeWithoutResult(transactie -> {
                    opslag.markeer(rekeningen.spaar(), YearMonth.of(2025, 12));
                    vastgelegd.countDown();
                    try {
                        assertThat(opnameWacht.await(60, TimeUnit.SECONDS)).as("de opname is onderweg").isTrue();
                    } catch (InterruptedException fout) {
                        throw new IllegalStateException(fout);
                    }
                    maandRente.boek(spaarrekening, YearMonth.of(2026, 2));
                });
            } catch (Throwable fout) {
                fouten.add(fout);
            }
        });
        assertThat(vastgelegd.await(30, TimeUnit.SECONDS)).as("de afsluiting heeft de lock (fouten: %s)", fouten).isTrue();
        draden.execute(() -> {
            try {
                ledger.boek(rekeningen.opname(tijdstip));
            } catch (Throwable fout) {
                fouten.add(fout);
            } finally {
                opnameKlaar.countDown();
            }
        });
        wachtTotIemandOpEenLockWachtOfKlaarIs(opnameKlaar);
        opnameWacht.countDown();
        draden.shutdown();

        assertThat(draden.awaitTermination(60, TimeUnit.SECONDS)).as("afsluiting en opname klaar").isTrue();
        assertThat(fouten).as("fouten bij de botsing").isEmpty();
        assertThat(mutaties(rekeningen.spaar())).contains(
                "2026-02-28 23.07 RENTE BankSim Rente februari",
                "2026-02-28 -1.15 RENTE BankSim Rentecorrectie februari");
        assertThat(opslag.geboekteRente(rekeningen.spaar(), YearMonth.of(2026, 2)))
                .isEqualTo(opslag.geboekteRente(zonderBotsing.spaar(), YearMonth.of(2026, 2)));
    }

    private List<String> zonderBotsing(long nummer, LocalDateTime tijdstip) {
        Rekeningen rekeningen = rekeningenMetJanuariBijgeschreven(nummer);
        ledger.boek(rekeningen.opname(tijdstip));
        rente.sluitAf(EIND_FEBRUARI);
        return mutaties(rekeningen.spaar());
    }

    private List<String> metBotsing(long nummer, LocalDateTime tijdstip) throws Exception {
        Rekeningen rekeningen = rekeningenMetJanuariBijgeschreven(nummer);
        assertThat(rekeningen.spaar().toString()).as("sorteert na de renterekening").isGreaterThan(MaandRente.BANKSIM.iban().toString());
        sluis.houdVast(rekeningen.spaar());

        var fouten = new ConcurrentLinkedQueue<Throwable>();
        var afsluitingKlaar = new CountDownLatch(1);
        ExecutorService draden = Executors.newFixedThreadPool(2);
        // execute en geen submit: een fout zou in een Future verdwijnen; hier komt hij in fouten terecht.
        draden.execute(() -> {
            try {
                ledger.boek(rekeningen.opname(tijdstip));
            } catch (Throwable fout) {
                fouten.add(fout);
            }
        });
        assertThat(sluis.gehouden().await(30, TimeUnit.SECONDS)).as("de opname heeft de spaarrekening (fouten: %s)", fouten).isTrue();
        draden.execute(() -> {
            try {
                rente.sluitAf(EIND_FEBRUARI);
            } catch (Throwable fout) {
                fouten.add(fout);
            } finally {
                afsluitingKlaar.countDown();
            }
        });
        wachtTotIemandOpEenLockWachtOfKlaarIs(afsluitingKlaar);
        sluis.laatGaan();
        draden.shutdown();

        assertThat(draden.awaitTermination(60, TimeUnit.SECONDS)).as("opname en afsluiting klaar").isTrue();
        assertThat(fouten).as("fouten bij de botsing").isEmpty();
        return mutaties(rekeningen.spaar());
    }

    /** PostgreSQL meldt zelf wie op een lock wacht; zo hangt de test niet af van een gegokte wachttijd. */
    private void wachtTotIemandOpEenLockWachtOfKlaarIs(CountDownLatch klaar) throws InterruptedException {
        for (int poging = 0; poging < 300; poging++) {
            long wachtend = jdbc.sql("""
                            SELECT count(*) FROM pg_stat_activity
                            WHERE datname = current_database() AND wait_event_type = 'Lock'""")
                    .query(Long.class).single();
            if (wachtend > 0 || klaar.getCount() == 0) {
                return;
            }
            Thread.sleep(100);
        }
        throw new AssertionError("Niemand wacht op een lock en de ander is ook niet klaar");
    }

    /** € 10.000,00 vanaf 1 januari, met de rente van januari bijgeschreven. */
    private Rekeningen rekeningenMetJanuariBijgeschreven(long nummer) {
        Iban spaar = rekening("botsing-" + nummer, nummer, "spaar", "10000.00", "2026-01-01");
        Iban betaal = rekening("botsing-" + nummer + "b", nummer + 1, "betaal", "0.00", null);
        rente.sluitAf(EIND_JANUARI);
        return new Rekeningen(spaar, betaal);
    }

    private Iban rekening(String gebruiker, long nummer, String soort, String saldo, String geopendOp) {
        Iban iban = Iban.simBank(nummer);
        jdbc.sql("INSERT INTO rekeninghouder (gebruikersnaam, naam) VALUES (:gebruiker, :gebruiker)").param("gebruiker", gebruiker)
                .update();
        jdbc.sql("""
                        INSERT INTO rekening (iban, rekeninghouder_id, soort, openingssaldo, geopend_op)
                        SELECT :iban, id, :soort, :saldo, :geopendOp FROM rekeninghouder WHERE gebruikersnaam = :gebruiker""")
                .param("iban", iban.toString()).param("soort", soort).param("saldo", new BigDecimal(saldo))
                .param("geopendOp", geopendOp == null ? null : LocalDate.parse(geopendOp))
                .param("gebruiker", gebruiker).update();
        return iban;
    }

    private List<String> mutaties(Iban iban) {
        return jdbc.sql("""
                        SELECT datum, bedrag, type, tegenpartij, omschrijving FROM transactie WHERE iban = :iban
                        ORDER BY datum, bedrag""")
                .param("iban", iban.toString())
                .query((rij, nummer) -> rij.getDate("datum") + " " + rij.getBigDecimal("bedrag").toPlainString() + " "
                        + rij.getString("type") + " " + rij.getString("tegenpartij") + " " + rij.getString("omschrijving"))
                .list();
    }

    private record Rekeningen(Iban spaar, Iban betaal) {

        Overboeking opname(LocalDateTime tijdstip) {
            return Overboeking.opname(new Partij(spaar, "Jan"), new Partij(betaal, "Jan"), Bedrag.van("1000.00"), tijdstip, "Opname");
        }
    }

    /**
     * Houdt één boeking op een rekening vast, in haar transactie: de rekeningen zijn dan vergrendeld, en de rente-code
     * heeft nog niets gedaan. Deze listener komt daarom vóór die van {@link RenteService}.
     */
    static class Sluis {

        private final AtomicReference<Iban> vastTeHouden = new AtomicReference<>();
        private volatile CountDownLatch gehouden = new CountDownLatch(1);
        private volatile CountDownLatch verder = new CountDownLatch(1);

        void houdVast(Iban rekening) {
            gehouden = new CountDownLatch(1);
            verder = new CountDownLatch(1);
            vastTeHouden.set(rekening);
        }

        CountDownLatch gehouden() {
            return gehouden;
        }

        void laatGaan() {
            verder.countDown();
        }

        @EventListener
        @Order(Ordered.HIGHEST_PRECEDENCE)
        void opBoeking(OverboekingGeboekt gebeurtenis) throws InterruptedException {
            Iban rekening = vastTeHouden.get();
            if (gebeurtenis.overboeking().van().iban().equals(rekening) && vastTeHouden.compareAndSet(rekening, null)) {
                gehouden.countDown();
                if (!verder.await(60, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("De sluis is niet opengegaan");
                }
            }
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class Opstelling {

        @Bean
        Sluis sluis() {
            return new Sluis();
        }
    }
}
