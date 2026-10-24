package nl.banksim.api.betaling;

import nl.banksim.domain.rekening.Iban;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** De contactenlijst in de database van bank-api (V5). */
@Repository
class JdbcContacten implements Contacten {

    private final JdbcClient jdbc;

    JdbcContacten(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public boolean bevat(Iban iban) {
        return jdbc.sql("SELECT EXISTS (SELECT 1 FROM contact WHERE iban = :iban)")
                .param("iban", iban.toString())
                .query(Boolean.class)
                .single();
    }
}
