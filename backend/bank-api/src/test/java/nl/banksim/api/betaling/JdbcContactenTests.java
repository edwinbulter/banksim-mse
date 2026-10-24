package nl.banksim.api.betaling;

import static org.assertj.core.api.Assertions.assertThat;

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

/** S7.1: de contactenlijst van de bank, tegen PostgreSQL met de migraties van bank-api. */
@Testcontainers
class JdbcContactenTests {

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(DockerImageName
            .parse("postgres:18.6-alpine@sha256:77f585114c32fbca283dc835b0596f4e52b51b4c6662d7810b2f4084f60a1873")
            .asCompatibleSubstituteFor("postgres"));

    static JdbcClient jdbc;

    @BeforeAll
    static void schemaEnContact() {
        var dataSource = new DriverManagerDataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        Flyway.configure().dataSource(dataSource).load().migrate();
        jdbc = JdbcClient.create(dataSource);
        jdbc.sql("INSERT INTO contact (iban, naam) VALUES ('NL67SIMB0000000002', 'Sanne')").update();
    }

    private final JdbcContacten contacten = new JdbcContacten(jdbc);

    @Test
    void bevatEenIbanUitDeContactenlijst() {
        assertThat(contacten.bevat(Iban.van("NL67SIMB0000000002"))).isTrue();
    }

    @Test
    void bevatGeenIbanDatNietInDeContactenlijstStaat() {
        assertThat(contacten.bevat(Iban.van("NL40SIMB0000000003"))).isFalse();
    }
}
