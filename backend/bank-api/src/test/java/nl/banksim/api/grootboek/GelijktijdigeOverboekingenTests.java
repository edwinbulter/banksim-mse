package nl.banksim.api.grootboek;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

import nl.banksim.api.PostgresTestDatabase;
import nl.banksim.domain.geld.Bedrag;
import nl.banksim.domain.grootboek.Overboeking;
import nl.banksim.domain.grootboek.Partij;
import nl.banksim.domain.rekening.Iban;
import nl.banksim.domain.transactie.TransactieType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * Fitness function (werkwijze §7, slice 7): 100 gelijktijdige overboekingen kriskras tussen 10 rekeningen. Geen
 * deadlock, het totaal blijft gelijk, en geen rekening komt onder € 0,00. Een weigering wegens saldo mag.
 */
@SpringBootTest(properties = {
        "banksim.cursor.sleutel=test-sleutel-van-minstens-32-tekens",
        "management.server.port="})
@Import(PostgresTestDatabase.class)
class GelijktijdigeOverboekingenTests {

    static final int REKENINGEN = 10;
    static final int OVERBOEKINGEN = 100;
    static final LocalDateTime TIJDSTIP = LocalDateTime.of(2026, 10, 5, 14, 0);

    @Autowired
    LedgerService ledger;

    @Autowired
    JdbcClient jdbc;

    @Autowired
    Grootboek grootboek;

    @MockitoBean
    JwtDecoder jwtDecoder;

    @Test
    void honderdGelijktijdigeOverboekingenKriskrasLatenGeenRekeningRoodEnHetTotaalGelijk() throws Exception {
        List<Iban> rekeningen = IntStream.rangeClosed(1, REKENINGEN).mapToObj(i -> Iban.simBank(1000L + i)).toList();
        for (int i = 0; i < REKENINGEN; i++) {
            String gebruiker = "gelijktijdig-" + i;
            jdbc.sql("INSERT INTO rekeninghouder (gebruikersnaam, naam) VALUES (:gebruiker, :gebruiker)")
                    .param("gebruiker", gebruiker).update();
            jdbc.sql("""
                            INSERT INTO rekening (iban, rekeninghouder_id, soort, openingssaldo)
                            SELECT :iban, id, 'betaal', 100.00 FROM rekeninghouder WHERE gebruikersnaam = :gebruiker""")
                    .param("iban", rekeningen.get(i).toString()).param("gebruiker", gebruiker).update();
        }

        var willekeurig = new Random(7);
        List<Overboeking> overboekingen = IntStream.range(0, OVERBOEKINGEN).mapToObj(n -> {
            int van = willekeurig.nextInt(REKENINGEN);
            int naar = (van + 1 + willekeurig.nextInt(REKENINGEN - 1)) % REKENINGEN;
            return new Overboeking(new Partij(rekeningen.get(van), "Van"), new Partij(rekeningen.get(naar), "Naar"),
                    Bedrag.van("30.00"), TransactieType.ONLINE_BANKIEREN, TIJDSTIP);
        }).toList();

        var start = new CountDownLatch(1);
        var geweigerd = new AtomicInteger();
        var fouten = new ConcurrentLinkedQueue<Throwable>();
        ExecutorService draden = Executors.newFixedThreadPool(20);
        for (Overboeking overboeking : overboekingen) {
            draden.submit(() -> {
                try {
                    start.await();
                    ledger.boek(overboeking);
                } catch (SaldoOntoereikend e) {
                    geweigerd.incrementAndGet();
                } catch (Throwable e) {
                    fouten.add(e);
                }
            });
        }
        start.countDown();
        draden.shutdown();

        assertThat(draden.awaitTermination(60, TimeUnit.SECONDS)).as("alle overboekingen klaar, geen deadlock").isTrue();
        assertThat(fouten).as("fouten anders dan saldo ontoereikend").isEmpty();
        List<BigDecimal> saldi = rekeningen.stream()
                .map(iban -> grootboek.saldoVerloop(iban, LocalDate.of(2026, 10, 5)).saldoOpUitvoerdatum().waarde())
                .toList();
        assertThat(saldi.stream().reduce(BigDecimal.ZERO, BigDecimal::add)).as("totaal").isEqualByComparingTo("1000.00");
        assertThat(saldi).as("saldi (geweigerd: %s)", geweigerd).allSatisfy(saldo -> assertThat(saldo).isNotNegative());
    }
}
