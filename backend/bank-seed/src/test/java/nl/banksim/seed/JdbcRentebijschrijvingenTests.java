package nl.banksim.seed;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.YearMonth;
import java.util.List;
import java.util.Optional;

import nl.banksim.domain.geld.Bedrag;
import nl.banksim.domain.rekening.Iban;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/** ADR 0043: tegen PostgreSQL met het schema van bank-api; de maand is de eerste dag ervan, zoals de maandafsluiting hem zoekt. */
@Testcontainers
class JdbcRentebijschrijvingenTests {

    @Container
    static final PostgreSQLContainer POSTGRES = TestDatabase.container();

    @Test
    void legtElkeMaandVastMetDeEersteDagVanDieMaand() {
        var dataSource = new DriverManagerDataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        Flyway.configure().dataSource(dataSource).locations(TestDatabase.MIGRATIES).load().migrate();
        JdbcClient jdbc = JdbcClient.create(dataSource);
        new JdbcRekeninghouders(jdbc).voegToeMetBetaalrekening("jan", "Jan", Iban.simBank(1L), Bedrag.van("0.00"));
        Iban spaar = new JdbcSpaarrekeningen(jdbc, () -> Iban.simBank(2L)).open("jan", Bedrag.van("0.00"), Optional.empty());

        new JdbcRentebijschrijvingen(jdbc).legVast(spaar, List.of(YearMonth.of(2021, 10), YearMonth.of(2021, 11)));

        assertThat(jdbc.sql("SELECT maand::text FROM rentebijschrijving WHERE iban = :iban ORDER BY maand")
                .param("iban", spaar.toString()).query(String.class).list())
                .containsExactly("2021-10-01", "2021-11-01");
    }
}
