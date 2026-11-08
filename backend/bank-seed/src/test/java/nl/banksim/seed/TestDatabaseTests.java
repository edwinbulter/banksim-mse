package nl.banksim.seed;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.SQLException;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.Timeout.ThreadMode;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Een PostgreSQL die niet antwoordt, laat een test falen in plaats van eeuwig wachten. Zonder grens bleef PIT
 * hangen in de inlog van een repositorytest (ADR 0046). {@code docker pause} geeft hetzelfde beeld: de verbinding
 * komt tot stand, maar er komt geen antwoord.
 */
@Testcontainers
class TestDatabaseTests {

    @Container
    static final PostgreSQLContainer POSTGRES = TestDatabase.container();

    private boolean stilgezet;

    @Test
    @Timeout(value = 30, unit = TimeUnit.SECONDS, threadMode = ThreadMode.SEPARATE_THREAD)
    void eenDatabaseDieNietAntwoordtGeeftBinnenDertigSecondenEenFout() {
        var dataSource = new DriverManagerDataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        POSTGRES.getDockerClient().pauseContainerCmd(POSTGRES.getContainerId()).exec();
        stilgezet = true;

        assertThatThrownBy(() -> dataSource.getConnection().close())
                .isInstanceOf(SQLException.class)
                .hasMessageContaining("timed out");
    }

    @AfterEach
    void weerAanzetten() {
        if (stilgezet) {
            POSTGRES.getDockerClient().unpauseContainerCmd(POSTGRES.getContainerId()).exec();
        }
    }
}
