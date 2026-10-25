package nl.banksim.api.rente;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import nl.banksim.api.PostgresTestDatabase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.scheduling.config.CronTask;
import org.springframework.scheduling.config.ScheduledTaskHolder;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * ADR 0028, met de echte applicatie en PostgreSQL: de maandafsluiting boekt de rente van elke afgesloten maand als
 * overboeking van de renterekening van BankSim naar de spaarrekening, hooguit één keer per rekening en maand.
 */
@SpringBootTest(properties = {
        "banksim.cursor.sleutel=test-sleutel-van-minstens-32-tekens",
        "management.server.port="})
@Import(PostgresTestDatabase.class)
class RenteServiceTests {

    static final String RENTEREKENING = "NL24SIMB0000000000";

    @Autowired
    RenteService rente;

    @Autowired
    JdbcClient jdbc;

    @MockitoBean
    JwtDecoder jwtDecoder;

    @Autowired
    ScheduledTaskHolder geplandeTaken;

    /** De nachtelijke afsluiting staat ingepland om 00:05 (ADR 0028). */
    @Test
    void deNachtelijkeAfsluitingIsIngeplandOm0005() {
        assertThat(geplandeTaken.getScheduledTasks()).extracting(taak -> taak.getTask())
                .filteredOn(CronTask.class::isInstance).map(CronTask.class::cast)
                .anySatisfy(taak -> {
                    assertThat(taak.getExpression()).isEqualTo("0 5 0 * * *");
                    assertThat(taak.toString()).contains("NachtelijkeAfsluiting.draai");
                });
    }

    /** € 10.000,00 vanaf 1 januari: € 25,48 op 31 januari, van "BankSim", met omschrijving "Rente januari". */
    @Test
    void boektDeRenteVanJanuariOpDeLaatsteDagVanDeMaand() {
        String spaar = spaarrekening("rente-1", 3001L, "10000.00", "2026-01-01");

        rente.sluitAf(LocalDate.of(2026, 2, 1));

        assertThat(mutaties(spaar)).containsExactly("2026-01-31 25.48 RENTE BankSim Rente januari");
        assertThat(jdbc.sql("SELECT bedrag FROM transactie WHERE iban = :iban AND tegen_iban = :spaar")
                .param("iban", RENTEREKENING).param("spaar", spaar).query(BigDecimal.class).single())
                .isEqualByComparingTo("-25.48");
    }

    @Test
    void tweeKeerAfsluitenBoektEenKeer() {
        String spaar = spaarrekening("rente-2", 3002L, "10000.00", "2026-01-01");

        rente.sluitAf(LocalDate.of(2026, 2, 1));
        rente.sluitAf(LocalDate.of(2026, 2, 15));

        assertThat(mutaties(spaar)).hasSize(1);
    }

    /** Een maand is pas afgesloten op haar laatste dag. */
    @Test
    void eenMaandDieNogNietVoorbijIsKrijgtGeenRente() {
        String spaar = spaarrekening("rente-3", 3003L, "10000.00", "2026-01-01");

        rente.sluitAf(LocalDate.of(2026, 1, 30));

        assertThat(mutaties(spaar)).isEmpty();
    }

    /** Geopend op 20 januari: 12 dagen rente, € 9,86. */
    @Test
    void deRenteBegintOpDeDagVanOpenen() {
        String spaar = spaarrekening("rente-4", 3004L, "10000.00", "2026-01-20");

        rente.sluitAf(LocalDate.of(2026, 2, 1));

        assertThat(mutaties(spaar)).containsExactly("2026-01-31 9.86 RENTE BankSim Rente januari");
    }

    /** Een storting op 16 januari telt vanaf die dag: 15 dagen € 10.000,00 en 16 dagen € 20.000,00 = € 38,63. */
    @Test
    void eenStortingHalverwegeDeMaandTeltVanafDieDag() {
        String spaar = spaarrekening("rente-6", 3006L, "10000.00", "2026-01-01");
        jdbc.sql("""
                        INSERT INTO transactie (iban, datum, bedrag, tegenpartij, type, tijdstip)
                        VALUES (:iban, '2026-01-16', 10000.00, 'Jan', 'OVERSCHRIJVING', '2026-01-16T10:00+01:00')""")
                .param("iban", spaar).update();

        rente.sluitAf(LocalDate.of(2026, 2, 1));

        assertThat(mutaties(spaar)).contains("2026-01-31 38.63 RENTE BankSim Rente januari");
    }

    /** Elke afgesloten maand sinds het openen; februari telt de rente van januari mee in het eindsaldo. */
    @Test
    void sluitAlleMaandenAfSindsHetOpenen() {
        String spaar = spaarrekening("rente-5", 3005L, "10000.00", "2026-01-01");

        rente.sluitAf(LocalDate.of(2026, 3, 31));

        assertThat(mutaties(spaar)).extracting(mutatie -> mutatie.substring(0, 10))
                .containsExactly("2026-01-31", "2026-02-28", "2026-03-31");
    }

    private String spaarrekening(String gebruiker, long nummer, String saldo, String geopendOp) {
        String iban = nl.banksim.domain.rekening.Iban.simBank(nummer).toString();
        jdbc.sql("INSERT INTO rekeninghouder (gebruikersnaam, naam) VALUES (:gebruiker, :gebruiker)").param("gebruiker", gebruiker)
                .update();
        jdbc.sql("""
                        INSERT INTO rekening (iban, rekeninghouder_id, soort, openingssaldo, geopend_op)
                        SELECT :iban, id, 'spaar', :saldo, :geopendOp FROM rekeninghouder WHERE gebruikersnaam = :gebruiker""")
                .param("iban", iban).param("saldo", new BigDecimal(saldo)).param("geopendOp", LocalDate.parse(geopendOp))
                .param("gebruiker", gebruiker).update();
        return iban;
    }

    private List<String> mutaties(String iban) {
        return jdbc.sql("""
                        SELECT datum, bedrag, type, tegenpartij, omschrijving FROM transactie WHERE iban = :iban
                        ORDER BY datum""")
                .param("iban", iban)
                .query((rij, nummer) -> rij.getDate("datum") + " " + rij.getBigDecimal("bedrag").toPlainString() + " "
                        + rij.getString("type") + " " + rij.getString("tegenpartij") + " " + rij.getString("omschrijving"))
                .list();
    }
}
