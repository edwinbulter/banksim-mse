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

/** ADR 0040: tegen PostgreSQL met het schema van bank-api; een mislukte login laat geen halve klant achter. */
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
                new JdbcTransacties(jdbc, transactie), nieuweIban,
                transactie, "klant-ww", "beheerder-ww");
    }

    private List<String> rekeningen(String gebruikersnaam) {
        return jdbc.sql("""
                        SELECT r.soort FROM rekening r JOIN rekeninghouder h ON h.id = r.rekeninghouder_id
                        WHERE h.gebruikersnaam = :gebruikersnaam ORDER BY r.soort""")
                .param("gebruikersnaam", gebruikersnaam).query(String.class).list();
    }

    private long transacties(String gebruikersnaam) {
        return jdbc.sql("""
                        SELECT count(*) FROM transactie t JOIN rekening r ON r.iban = t.iban
                        JOIN rekeninghouder h ON h.id = r.rekeninghouder_id
                        WHERE h.gebruikersnaam = :gebruikersnaam""")
                .param("gebruikersnaam", gebruikersnaam).query(Long.class).single();
    }

    /** De ene test, want de gebruikersnamen liggen vast: mislukken, en daarna afmaken en herhalen op dezelfde database. */
    @Test
    void eenMislukteLoginLaatGeenHalveKlantAchterEnDeVolgendeKeerWordtHetAfgemaakt() {
        faaltVoor = "ljansen";
        assertThatThrownBy(() -> demo().maak()).hasMessage("Keycloak is weg");

        assertThat(rekeningen("melamrani")).containsExactly("betaal", "spaar");
        assertThat(transacties("melamrani")).isEqualTo(126);
        assertThat(rekeningen("ljansen")).isEmpty();
        assertThat(transacties("ljansen")).isZero();

        faaltVoor = null;
        logins.clear();
        demo().maak();

        assertThat(rekeningen("ljansen")).containsExactly("betaal", "spaar");
        assertThat(transacties("ljansen")).isEqualTo(126);
        assertThat(rekeningen("nhendriks")).containsExactly("betaal", "spaar");
        assertThat(logins).doesNotContain("jdevries", "melamrani").contains("ljansen", "nhendriks", "beheerder");

        long aantal = jdbc.sql("SELECT count(*) FROM rekening").query(Long.class).single();
        long geboekt = jdbc.sql("SELECT count(*) FROM transactie").query(Long.class).single();
        logins.clear();
        demo().maak();

        assertThat(jdbc.sql("SELECT count(*) FROM rekening").query(Long.class).single()).isEqualTo(aantal);
        assertThat(jdbc.sql("SELECT count(*) FROM transactie").query(Long.class).single()).isEqualTo(geboekt);
        assertThat(logins).containsExactly("beheerder");
    }
}
