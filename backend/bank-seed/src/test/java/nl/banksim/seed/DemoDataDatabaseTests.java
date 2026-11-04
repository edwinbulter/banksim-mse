package nl.banksim.seed;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

import nl.banksim.domain.rekening.Iban;
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

/** ADR 0040, 0044: tegen PostgreSQL met het schema van bank-api; een mislukte login laat niets achter. */
@Testcontainers
class DemoDataDatabaseTests {

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(TestDatabase.IMAGE);

    static JdbcClient jdbc;
    static TransactionTemplate transactie;

    @BeforeAll
    static void schema() {
        var dataSource = new DriverManagerDataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        Flyway.configure().dataSource(dataSource).locations(TestDatabase.MIGRATIES).load().migrate();
        jdbc = JdbcClient.create(dataSource);
        transactie = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
    }

    private final AtomicLong laatsteIban = new AtomicLong(5000);
    private final List<String> logins = new ArrayList<>();
    private String faaltVoor;
    private final DemoLogins keycloak = new DemoLogins() {
        @Override
        public void zorgVoorKlant(String gebruikersnaam, String wachtwoord) {
            if (gebruikersnaam.equals(faaltVoor)) {
                throw new IllegalStateException("Keycloak is weg");
            }
            logins.add(gebruikersnaam);
        }

        @Override
        public void zorgVoorBeheerder(String gebruikersnaam, String wachtwoord) {
            logins.add(gebruikersnaam);
        }
    };

    private DemoData demo() {
        var rekeninghouders = new JdbcRekeninghouders(jdbc);
        Supplier<Iban> nieuweIban = () -> Iban.simBank(laatsteIban.incrementAndGet());
        return new DemoData(keycloak, rekeninghouders, rekeninghouders, new JdbcSpaarrekeningen(jdbc, nieuweIban),
                new JdbcTransacties(jdbc, transactie), new JdbcOverboekingen(jdbc, transactie), new JdbcRentebijschrijvingen(jdbc), nieuweIban,
                transactie, "klant-ww", "beheerder-ww");
    }

    private List<String> rekeningen(String gebruikersnaam) {
        return jdbc.sql("""
                        SELECT r.soort FROM rekening r JOIN rekeninghouder h ON h.id = r.rekeninghouder_id
                        WHERE h.gebruikersnaam = :gebruikersnaam ORDER BY r.soort""")
                .param("gebruikersnaam", gebruikersnaam).query(String.class).list();
    }

    /** De eenzijdige boekingen, de twee kanten van elke inleg en opname (ADR 0042), de rente (ADR 0043) en de betalingen tussen klanten (ADR 0044). */
    private static long aantalBoekingen(String gebruikersnaam) {
        var klant = DemoKlanten.ALLE.stream()
                .filter(demo -> demo.gebruikersnaam().equals(gebruikersnaam))
                .findFirst().orElseThrow();
        return Generator.boekingen(klant).size() + 2L * (Generator.inleggen(klant).size() + Generator.opnames(klant).size())
                + Generator.rente(klant).size()
                + Generator.betalingenTussenKlanten(DemoKlanten.ALLE).stream()
                        .filter(betaling -> betaling.van().equals(gebruikersnaam) || betaling.naar().equals(gebruikersnaam)).count();
    }

    private long transacties(String gebruikersnaam) {
        return jdbc.sql("""
                        SELECT count(*) FROM transactie t JOIN rekening r ON r.iban = t.iban
                        JOIN rekeninghouder h ON h.id = r.rekeninghouder_id
                        WHERE h.gebruikersnaam = :gebruikersnaam""")
                .param("gebruikersnaam", gebruikersnaam).query(Long.class).single();
    }

    /** De ene test, want de gebruikersnamen liggen vast: mislukken, en daarna opnieuw maken en herhalen op dezelfde database (ADR 0044). */
    @Test
    void eenMislukteLoginLaatNietsAchterEnDeVolgendeKeerOntstaatAlles() {
        faaltVoor = "ljansen";
        assertThatThrownBy(() -> demo().maak()).hasMessage("Keycloak is weg");

        assertThat(rekeningen("jdevries")).isEmpty();
        assertThat(rekeningen("ljansen")).isEmpty();
        assertThat(jdbc.sql("SELECT count(*) FROM transactie").query(Long.class).single()).isZero();
        assertThat(jdbc.sql("SELECT count(*) FROM overboeking").query(Long.class).single()).isZero();
        assertThat(jdbc.sql("SELECT count(*) FROM rentebijschrijving").query(Long.class).single()).isZero();

        faaltVoor = null;
        logins.clear();
        demo().maak();

        assertThat(rekeningen("jdevries")).containsExactly("betaal", "spaar");
        assertThat(rekeningen("nhendriks")).containsExactly("betaal", "spaar");
        for (DemoKlanten.DemoKlant klant : DemoKlanten.ALLE) {
            assertThat(transacties(klant.gebruikersnaam())).as(klant.gebruikersnaam()).isEqualTo(aantalBoekingen(klant.gebruikersnaam()));
        }
        assertThat(logins).contains("jdevries", "ljansen", "nhendriks", "beheerder");

        long aantal = jdbc.sql("SELECT count(*) FROM rekening").query(Long.class).single();
        long geboekt = jdbc.sql("SELECT count(*) FROM transactie").query(Long.class).single();
        logins.clear();
        demo().maak();

        assertThat(jdbc.sql("SELECT count(*) FROM rekening").query(Long.class).single()).isEqualTo(aantal);
        assertThat(jdbc.sql("SELECT count(*) FROM transactie").query(Long.class).single()).isEqualTo(geboekt);
        assertThat(logins).containsExactly("beheerder");
    }
}
