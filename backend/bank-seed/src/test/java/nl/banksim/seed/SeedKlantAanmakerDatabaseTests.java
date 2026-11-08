package nl.banksim.seed;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import nl.banksim.domain.geld.Bedrag;
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

/** Backlog fase 4.1: een klant is de rekeninghouder én de login, of geen van beide; tegen PostgreSQL (ADR 0009). */
@Testcontainers
class SeedKlantAanmakerDatabaseTests {

    @Container
    static final PostgreSQLContainer POSTGRES = TestDatabase.container();

    static JdbcClient jdbc;
    static TransactionTemplate transactie;

    @BeforeAll
    static void schema() {
        var dataSource = new DriverManagerDataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        Flyway.configure().dataSource(dataSource).locations(TestDatabase.MIGRATIES).load().migrate();
        jdbc = JdbcClient.create(dataSource);
        transactie = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
    }

    private static final AtomicLong VOLGENDE = new AtomicLong(1);
    private final List<String> logins = new ArrayList<>();

    private SeedKlantAanmaker aanmaker(Logins keycloak) {
        return new SeedKlantAanmaker(keycloak, new JdbcRekeninghouders(jdbc),
                () -> Iban.simBank(VOLGENDE.getAndIncrement()), transactie);
    }

    @Test
    void eenMislukteLoginLaatGeenRekeninghouderAchter() {
        Logins keycloakOnbereikbaar = gebruikersnaam -> {
            throw new IllegalStateException("Keycloak onbereikbaar");
        };

        assertThatThrownBy(() -> aanmaker(keycloakOnbereikbaar).klantMetBetaalrekening("piet-a1-1", "Piet", Bedrag.van("10.00")))
                .hasMessage("Keycloak onbereikbaar");

        assertThat(jdbc.sql("SELECT count(*) FROM rekeninghouder WHERE gebruikersnaam = 'piet-a1-1'").query(Long.class).single())
                .isZero();
    }

    @Test
    void dezelfdeKlantNogEenKeerIsEenDuidelijkeFoutZonderNieuweLogin() {
        Logins keycloak = gebruikersnaam -> {
            logins.add(gebruikersnaam);
            return "wachtwoord";
        };
        aanmaker(keycloak).klantMetBetaalrekening("kees-a1-1", "Kees", Bedrag.van("10.00"));

        assertThatThrownBy(() -> aanmaker(keycloak).klantMetBetaalrekening("kees-a1-1", "Kees", Bedrag.van("10.00")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Klant kees-a1-1 bestaat al");
        assertThat(logins).containsExactly("kees-a1-1");
    }
}
