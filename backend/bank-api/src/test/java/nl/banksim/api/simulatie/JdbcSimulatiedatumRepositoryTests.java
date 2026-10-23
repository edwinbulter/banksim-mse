package nl.banksim.api.simulatie;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/** ADR 0013: hooguit één simulatiedatum, in tabel instelling; geen rij betekent vandaag. */
@Testcontainers
class JdbcSimulatiedatumRepositoryTests {

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(DockerImageName
            .parse("postgres:18.6-alpine@sha256:77f585114c32fbca283dc835b0596f4e52b51b4c6662d7810b2f4084f60a1873")
            .asCompatibleSubstituteFor("postgres"));

    static JdbcClient jdbc;

    @BeforeAll
    static void schema() {
        var dataSource = new DriverManagerDataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        Flyway.configure().dataSource(dataSource).load().migrate();
        jdbc = JdbcClient.create(dataSource);
    }

    private final JdbcSimulatiedatumRepository repository = new JdbcSimulatiedatumRepository(jdbc);

    @BeforeEach
    void leeg() {
        repository.wis();
    }

    @Test
    void zonderSimulatiedatumIsErNiets() {
        assertThat(repository.lees()).isEmpty();
    }

    @Test
    void eenNieuweDatumVervangtDeVorige() {
        repository.zet(LocalDate.of(2026, 10, 5));
        repository.zet(LocalDate.of(2026, 10, 15));

        assertThat(repository.lees()).contains(LocalDate.of(2026, 10, 15));
        assertThat(jdbc.sql("SELECT count(*) FROM instelling").query(Long.class).single()).isEqualTo(1L);
    }

    @Test
    void wissenLaatGeenSimulatiedatumAchter() {
        repository.zet(LocalDate.of(2026, 10, 5));
        repository.wis();

        assertThat(repository.lees()).isEmpty();
    }
}
