package nl.banksim.api.rekening;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;

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
import org.testcontainers.utility.DockerImageName;

/** Tegen dezelfde PostgreSQL als in de chart, met de migraties die bank-api bij het starten uitvoert (ADR 0009). */
@Testcontainers
class JdbcRekeningRepositoryTests {

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(DockerImageName
            .parse("postgres:18.6-alpine@sha256:77f585114c32fbca283dc835b0596f4e52b51b4c6662d7810b2f4084f60a1873")
            .asCompatibleSubstituteFor("postgres"));

    static JdbcClient jdbc;

    @BeforeAll
    static void schemaEnRekeningen() {
        var dataSource = new DriverManagerDataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        Flyway.configure().dataSource(dataSource).load().migrate();
        jdbc = JdbcClient.create(dataSource);
        jdbc.sql("INSERT INTO rekeninghouder (gebruikersnaam, naam) VALUES ('jan', 'Jan'), ('piet', 'Piet'), ('kees', 'Kees')").update();
        jdbc.sql("""
                INSERT INTO rekening (iban, rekeninghouder_id, soort, openingssaldo)
                SELECT 'NL94SIMB0000000001', id, 'betaal', 1842.17 FROM rekeninghouder WHERE gebruikersnaam = 'jan'
                UNION ALL
                SELECT 'NL38SIMB0123456789', id, 'betaal', 5.00 FROM rekeninghouder WHERE gebruikersnaam = 'piet'
                UNION ALL
                SELECT 'NL08SIMB2010265342', id, 'betaal', 1000.00 FROM rekeninghouder WHERE gebruikersnaam = 'kees'
                """).update();
        jdbc.sql("""
                INSERT INTO transactie (iban, datum, bedrag, tegenpartij, tijdstip) VALUES
                ('NL08SIMB2010265342', '2026-10-01', -812.40, 'Woningstichting Thuis', '2026-10-01T06:02:00+02:00'),
                ('NL08SIMB2010265342', '2026-10-02', 3215.00, 'Werkgever B.V.', '2026-10-02T09:15:00+02:00')
                """).update();
    }

    /** Na alle transacties in deze test, zodat de tests niet afhangen van de dag waarop ze draaien. */
    static final LocalDate NA_ALLES = LocalDate.of(2026, 12, 31);

    private final JdbcRekeningRepository repository = new JdbcRekeningRepository(jdbc);

    @Test
    void geeftAlleenDeRekeningenVanDeGebruikerMetOpeningssaldoAlsSaldo() {
        assertThat(repository.vanGebruiker("jan", NA_ALLES)).containsExactly(
                new Rekening(Soort.BETAAL, Iban.van("NL94SIMB0000000001"), Bedrag.van("1842.17")));
    }

    /** S8.2: ook de spaarrekening, na de betaalrekening. */
    @Test
    void geeftOokDeSpaarrekeningNaDeBetaalrekening() {
        jdbc.sql("INSERT INTO rekeninghouder (gebruikersnaam, naam) VALUES ('sanne', 'Sanne')").update();
        jdbc.sql("""
                INSERT INTO rekening (iban, rekeninghouder_id, soort, openingssaldo)
                SELECT 'NL67SIMB0000000002', id, 'spaar', 12405.63 FROM rekeninghouder WHERE gebruikersnaam = 'sanne'
                UNION ALL
                SELECT 'NL40SIMB0000000003', id, 'betaal', 1842.17 FROM rekeninghouder WHERE gebruikersnaam = 'sanne'
                """).update();

        assertThat(repository.vanGebruiker("sanne", NA_ALLES)).containsExactly(
                new Rekening(Soort.BETAAL, Iban.van("NL40SIMB0000000003"), Bedrag.van("1842.17")),
                new Rekening(Soort.SPAAR, Iban.van("NL67SIMB0000000002"), Bedrag.van("12405.63")));
    }

    @Test
    void hetSaldoIsHetOpeningssaldoPlusDeTransacties() {
        assertThat(repository.vanGebruiker("kees", NA_ALLES)).containsExactly(
                new Rekening(Soort.BETAAL, Iban.van("NL08SIMB2010265342"), Bedrag.van("3402.60")));
    }

    /** ADR 0013: een transactie na "vandaag" telt niet mee; een transactie óp vandaag wel. */
    @Test
    void hetSaldoTeltAlleenDeTransactiesTotEnMetVandaag() {
        assertThat(repository.vanGebruiker("kees", LocalDate.of(2026, 10, 1))).containsExactly(
                new Rekening(Soort.BETAAL, Iban.van("NL08SIMB2010265342"), Bedrag.van("187.60")));
    }

    @Test
    void geeftNietsVoorEenOnbekendeGebruiker() {
        assertThat(repository.vanGebruiker("onbekend", NA_ALLES)).isEmpty();
    }
}
