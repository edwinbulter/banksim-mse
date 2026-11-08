package nl.banksim.api.beheer;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.Optional;

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

/** S10.1, tegen PostgreSQL met de migraties van bank-api: de rekeninghouders voor de beheerder. */
@Testcontainers
class JdbcRekeninghoudersTests {

    @Container
    static final PostgreSQLContainer POSTGRES = PostgresTestDatabase.container();

    static final Iban BETAAL_JAN = Iban.simBank(1L);
    static final Iban SPAAR_JAN = Iban.simBank(2L);
    static final Iban BETAAL_PIET = Iban.simBank(3L);
    static final LocalDate VANDAAG = LocalDate.of(2026, 10, 5);

    static JdbcClient jdbc;

    /** Jan met een betaal- en een spaarrekening, en een transactie vóór en ná vandaag; Piet zonder spaarrekening. */
    @BeforeAll
    static void rekeninghouders() {
        var dataSource = new DriverManagerDataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        Flyway.configure().dataSource(dataSource).load().migrate();
        jdbc = JdbcClient.create(dataSource);
        rekeninghouder("jan", "Jan de Vries");
        rekeninghouder("piet", "Piet Bakker");
        rekening("jan", BETAAL_JAN, "betaal", "1000.00");
        rekening("jan", SPAAR_JAN, "spaar", "12405.63");
        rekening("piet", BETAAL_PIET, "betaal", "5.00");
        transactie(BETAAL_JAN, "2026-10-01", "842.17");
        transactie(BETAAL_JAN, "2026-10-06", "-100.00");
    }

    private final JdbcRekeninghouders rekeninghouders = new JdbcRekeninghouders(jdbc);

    /** Een deel van de naam, zonder hoofdletters; de saldi aan het eind van vandaag (ADR 0013). */
    @Test
    void vindtOpEenDeelVanDeNaamMetDeSaldiVanVandaag() {
        assertThat(rekeninghouders.zoek("DE VRIES", VANDAAG)).containsExactly(new Rekeninghouder("Jan de Vries",
                BETAAL_JAN, Bedrag.van("1842.17"), Optional.of(SPAAR_JAN), Optional.of(Bedrag.van("12405.63"))));
    }

    /** Het rekeningnummer van de betaal- of de spaarrekening, ook met spaties en kleine letters. */
    @Test
    void vindtOpHetRekeningnummerVanDeBetaalOfDeSpaarrekening() {
        String betaal = BETAAL_JAN.toString().toLowerCase(java.util.Locale.ROOT);
        assertThat(rekeninghouders.zoek(betaal.substring(0, 4) + " " + betaal.substring(4), VANDAAG))
                .extracting(Rekeninghouder::naam).containsExactly("Jan de Vries");
        assertThat(rekeninghouders.zoek(SPAAR_JAN.toString(), VANDAAG)).extracting(Rekeninghouder::naam)
                .containsExactly("Jan de Vries");
    }

    @Test
    void zonderSpaarrekeningOntbrekenSpaarrekeningEnSpaarsaldo() {
        assertThat(rekeninghouders.zoek("piet", VANDAAG)).containsExactly(new Rekeninghouder("Piet Bakker", BETAAL_PIET,
                Bedrag.van("5.00"), Optional.empty(), Optional.empty()));
    }

    /**
     * Zonder zoektekst iedereen met een betaalrekening, op naam; de rekeninghouder BankSim met alleen de renterekening
     * (V11) niet. (Andere tests voegen rekeninghouders toe, dus niet "precies deze".)
     */
    @Test
    void zonderZoektekstIedereenMetEenBetaalrekeningOpNaam() {
        assertThat(rekeninghouders.zoek("", VANDAAG)).extracting(Rekeninghouder::naam)
                .contains("Jan de Vries", "Piet Bakker").doesNotContain("BankSim").isSorted();
    }

    /** Hoogstens 50, zodat het scherm bruikbaar blijft met veel klanten in de testdata. */
    @Test
    void hoogstens50() {
        for (int i = 0; i < 51; i++) {
            rekeninghouder("veel-" + i, "Veel " + String.format("%02d", i));
            rekening("veel-" + i, Iban.simBank(100L + i), "betaal", "1.00");
        }

        assertThat(rekeninghouders.zoek("veel", VANDAAG)).hasSize(50).first().extracting(Rekeninghouder::naam)
                .isEqualTo("Veel 00");
    }

    private static void rekeninghouder(String gebruikersnaam, String naam) {
        jdbc.sql("INSERT INTO rekeninghouder (gebruikersnaam, naam) VALUES (:gebruikersnaam, :naam)")
                .param("gebruikersnaam", gebruikersnaam).param("naam", naam).update();
    }

    private static void rekening(String gebruikersnaam, Iban iban, String soort, String saldo) {
        jdbc.sql("""
                        INSERT INTO rekening (iban, rekeninghouder_id, soort, openingssaldo)
                        SELECT :iban, id, :soort, CAST(:saldo AS numeric) FROM rekeninghouder WHERE gebruikersnaam = :gebruikersnaam""")
                .param("iban", iban.toString()).param("soort", soort).param("saldo", saldo)
                .param("gebruikersnaam", gebruikersnaam).update();
    }

    private static void transactie(Iban iban, String datum, String bedrag) {
        jdbc.sql("""
                        INSERT INTO transactie (iban, datum, bedrag, tegenpartij, tijdstip)
                        VALUES (:iban, CAST(:datum AS date), CAST(:bedrag AS numeric), 'Iemand', CAST(:datum AS date))""")
                .param("iban", iban.toString()).param("datum", datum).param("bedrag", bedrag).update();
    }
}
