package nl.banksim.api.grootboek;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDateTime;

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
 * ADR 0024: LedgerService boekt beide kanten in één databasetransactie. Zonder transactie zou de trigger al bij de
 * eerste transactie afgaan, want die commit dan los.
 */
@SpringBootTest(properties = {
        "banksim.cursor.sleutel=test-sleutel-van-minstens-32-tekens",
        "management.server.port="})
@Import(PostgresTestDatabase.class)
class LedgerServiceTests {

    static final Iban JAN = Iban.van("NL94SIMB0000000001");
    static final Iban SANNE = Iban.van("NL67SIMB0000000002");

    @Autowired
    LedgerService ledger;

    @Autowired
    JdbcClient jdbc;

    @MockitoBean
    JwtDecoder jwtDecoder;

    @Test
    void boektBeideKantenInEenDatabasetransactie() {
        jdbc.sql("INSERT INTO rekeninghouder (gebruikersnaam, naam) VALUES ('jan', 'Jan'), ('sanne', 'Sanne')").update();
        jdbc.sql("""
                INSERT INTO rekening (iban, rekeninghouder_id, soort, openingssaldo)
                SELECT 'NL94SIMB0000000001', id, 'betaal', 500.00 FROM rekeninghouder WHERE gebruikersnaam = 'jan'
                UNION ALL
                SELECT 'NL67SIMB0000000002', id, 'betaal', 0.00 FROM rekeninghouder WHERE gebruikersnaam = 'sanne'
                """).update();

        ledger.boek(new Overboeking(new Partij(JAN, "Jan"), new Partij(SANNE, "Sanne"), Bedrag.van("25.00"),
                TransactieType.ONLINE_BANKIEREN, LocalDateTime.of(2026, 10, 5, 14, 0)));

        assertThat(jdbc.sql("SELECT iban, bedrag FROM transactie ORDER BY bedrag").query().listOfRows())
                .extracting(rij -> rij.get("iban") + " " + rij.get("bedrag"))
                .containsExactly(JAN + " " + new BigDecimal("-25.00"), SANNE + " " + new BigDecimal("25.00"));
    }
}
