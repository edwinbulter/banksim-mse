package nl.banksim.seed;

import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

final class TestDatabase {

    /** Dezelfde PostgreSQL als in de chart (values.yaml, ADR 0009). */
    private static final DockerImageName IMAGE = DockerImageName
            .parse("postgres:18.6-alpine@sha256:77f585114c32fbca283dc835b0596f4e52b51b4c6662d7810b2f4084f60a1873")
            .asCompatibleSubstituteFor("postgres");

    /** Het schema is van bank-api; bank-seed schrijft erin (ADR 0003). */
    static final String MIGRATIES = "filesystem:../bank-api/src/main/resources/db/migration";

    /** De inlog duurt hoogstens 10 seconden: zonder grens bleef PIT hangen als PostgreSQL niet antwoordde (ADR 0046). */
    static PostgreSQLContainer container() {
        return new PostgreSQLContainer(IMAGE).withUrlParam("loginTimeout", "10");
    }

    private TestDatabase() {
    }
}
