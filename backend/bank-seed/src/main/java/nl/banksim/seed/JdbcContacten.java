package nl.banksim.seed;

import nl.banksim.domain.rekening.Iban;
import org.springframework.jdbc.core.simple.JdbcClient;

/** Schrijft in het schema van bank-api, als databasegebruiker bank_seed (alleen SELECT en INSERT). */
class JdbcContacten implements Contacten {

    private final JdbcClient jdbc;

    JdbcContacten(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void voegToe(Iban iban, String naam) {
        jdbc.sql("INSERT INTO contact (iban, naam) VALUES (:iban, :naam)")
                .param("iban", iban.toString())
                .param("naam", naam)
                .update();
    }
}
