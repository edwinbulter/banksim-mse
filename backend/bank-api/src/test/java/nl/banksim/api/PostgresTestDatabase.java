package nl.banksim.api;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/** Dezelfde PostgreSQL als in de chart, voor tests met de echte applicatie (Flyway migreert bij het starten). */
@TestConfiguration(proxyBeanMethods = false)
public class PostgresTestDatabase {

    @Bean
    @ServiceConnection
    PostgreSQLContainer postgres() {
        return new PostgreSQLContainer(DockerImageName
                .parse("postgres:18.6-alpine@sha256:77f585114c32fbca283dc835b0596f4e52b51b4c6662d7810b2f4084f60a1873")
                .asCompatibleSubstituteFor("postgres"));
    }
}
