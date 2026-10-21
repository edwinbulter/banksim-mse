package nl.banksim.seed;

import java.time.LocalDate;

import nl.banksim.domain.geld.Bedrag;
import nl.banksim.domain.rekening.Iban;
import org.springframework.jdbc.core.simple.JdbcClient;

/** Schrijft in het schema van bank-api, als databasegebruiker bank_seed (alleen SELECT en INSERT). */
class JdbcTransacties implements Transacties {

    private final JdbcClient jdbc;

    JdbcTransacties(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void boek(Iban iban, LocalDate datum, Bedrag bedrag, String tegenpartij) {
        jdbc.sql("""
                        INSERT INTO transactie (iban, datum, bedrag, tegenpartij)
                        VALUES (:iban, :datum, :bedrag, :tegenpartij)""")
                .param("iban", iban.toString())
                .param("datum", datum)
                .param("bedrag", bedrag.waarde())
                .param("tegenpartij", tegenpartij)
                .update();
    }
}
