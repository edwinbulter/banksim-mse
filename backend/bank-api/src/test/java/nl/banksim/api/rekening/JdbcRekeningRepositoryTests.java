package nl.banksim.api.rekening;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;

import nl.banksim.api.PostgresTestDatabase;
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

/** Tegen dezelfde PostgreSQL als in de chart, met de migraties die bank-api bij het starten uitvoert (ADR 0009). */
@Testcontainers
class JdbcRekeningRepositoryTests {

    @Container
    static final PostgreSQLContainer POSTGRES = PostgresTestDatabase.container();

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

    /**
     * Backlog fase 7.4: de interne rekening van de bank (soort intern, V11) hoort niet in het overzicht van een klant.
     * Zonder filter gaf Soort.van een IllegalArgumentException, en ProbleemAdvice maakte daar een 400 van.
     */
    @Test
    void eenInterneRekeningHoortNietInHetOverzicht() {
        Iban betaal = Iban.simBank(4L);
        jdbc.sql("""
                INSERT INTO rekening (iban, rekeninghouder_id, soort, openingssaldo)
                SELECT :iban, id, 'betaal', 10.00 FROM rekeninghouder WHERE gebruikersnaam = 'banksim'
                """).param("iban", betaal.toString()).update();

        assertThat(repository.vanGebruiker("banksim", NA_ALLES)).containsExactly(
                new Rekening(Soort.BETAAL, betaal, Bedrag.van("10.00")));
    }

    @Test
    void geeftNietsVoorEenOnbekendeGebruiker() {
        assertThat(repository.vanGebruiker("onbekend", NA_ALLES)).isEmpty();
    }

    /** FO "Kopgedeelte": een eigen rekening met de naam van de rekeninghouder, en het saldo met de transacties. */
    @Test
    void eenEigenRekeningHeeftDeNaamVanDeRekeninghouderEnHetSaldo() {
        assertThat(repository.vanGebruiker("kees", Iban.van("NL08SIMB2010265342"), NA_ALLES)).contains(
                new RekeningMetHouder(Soort.BETAAL, Iban.van("NL08SIMB2010265342"), Bedrag.van("3402.60"), "Kees"));
    }

    /** ADR 0013: ook in de kop telt een transactie na "vandaag" niet mee. */
    @Test
    void hetSaldoVanEenEigenRekeningTeltAlleenDeTransactiesTotEnMetVandaag() {
        assertThat(repository.vanGebruiker("kees", Iban.van("NL08SIMB2010265342"), LocalDate.of(2026, 10, 1)))
                .map(RekeningMetHouder::saldo)
                .contains(Bedrag.van("187.60"));
    }

    /** ADR 0023: een rekening van een ander bestaat voor de klant niet. */
    @Test
    void eenRekeningVanEenAnderIsLeeg() {
        assertThat(repository.vanGebruiker("jan", Iban.van("NL08SIMB2010265342"), NA_ALLES)).isEmpty();
    }

    /** ADR 0038: de beheerder ziet elke rekening van een klant, met de naam en het saldo op vandaag. */
    @Test
    void deBeheerderZietElkeRekeningVanEenKlant() {
        assertThat(repository.inBeheer(Iban.van("NL08SIMB2010265342"), LocalDate.of(2026, 10, 1))).contains(
                new RekeningMetHouder(Soort.BETAAL, Iban.van("NL08SIMB2010265342"), Bedrag.van("187.60"), "Kees"));
    }

    /** De interne rekening van de bank (V11) is geen rekening van een klant, ook niet voor de beheerder. */
    @Test
    void deInterneRekeningIsInBeheerLeeg() {
        assertThat(repository.inBeheer(Iban.van("NL24SIMB0000000000"), NA_ALLES)).isEmpty();
    }

    @Test
    void eenOnbekendeRekeningIsInBeheerLeeg() {
        assertThat(repository.inBeheer(Iban.simBank(9L), NA_ALLES)).isEmpty();
    }
}
