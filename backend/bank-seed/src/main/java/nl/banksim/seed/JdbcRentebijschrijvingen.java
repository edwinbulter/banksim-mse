package nl.banksim.seed;

import java.time.YearMonth;
import java.util.List;

import nl.banksim.domain.rekening.Iban;
import org.springframework.jdbc.core.simple.JdbcClient;

/** ADR 0043: schrijft in het schema van bank-api, als databasegebruiker bank_seed (alleen SELECT en INSERT). */
class JdbcRentebijschrijvingen implements Rentebijschrijvingen {

    private final JdbcClient jdbc;

    JdbcRentebijschrijvingen(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void legVast(Iban spaarrekening, List<YearMonth> maanden) {
        for (YearMonth maand : maanden) {
            jdbc.sql("INSERT INTO rentebijschrijving (iban, maand) VALUES (:iban, :maand)")
                    .param("iban", spaarrekening.toString())
                    .param("maand", maand.atDay(1))
                    .update();
        }
    }
}
