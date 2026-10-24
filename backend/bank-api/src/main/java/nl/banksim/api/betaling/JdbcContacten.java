package nl.banksim.api.betaling;

import java.util.Optional;

import nl.banksim.domain.grootboek.Contact;
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
    public Optional<Contact> vind(Iban iban) {
        return jdbc.sql("SELECT naam FROM contact WHERE iban = :iban")
                .param("iban", iban.toString())
                .query(String.class)
                .optional()
                .map(naam -> new Contact(iban, naam));
    }
}
