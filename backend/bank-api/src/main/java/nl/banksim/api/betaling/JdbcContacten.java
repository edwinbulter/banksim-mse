package nl.banksim.api.betaling;

import java.util.List;
import java.util.Locale;
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

    /** Naam: een deel, zonder hoofdletters; IBAN: het begin, zonder spaties. %, _ en \\ zijn gewone tekens. */
    @Override
    public List<Contact> zoek(String tekst, String gebruikersnaam) {
        String gewoon = tekst.strip().replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
        return jdbc.sql("""
                        SELECT c.iban, c.naam FROM contact c
                        WHERE (c.naam ILIKE :naam ESCAPE '\\' OR c.iban LIKE :iban ESCAPE '\\')
                          AND NOT EXISTS (SELECT 1 FROM rekening r JOIN rekeninghouder h ON h.id = r.rekeninghouder_id
                                          WHERE r.iban = c.iban AND h.gebruikersnaam = :gebruikersnaam)
                        ORDER BY c.naam LIMIT 10""")
                .param("naam", "%" + gewoon + "%")
                .param("iban", gewoon.replace(" ", "").toUpperCase(Locale.ROOT) + "%")
                .param("gebruikersnaam", gebruikersnaam)
                .query((rij, nummer) -> new Contact(Iban.van(rij.getString("iban")), rij.getString("naam")))
                .list();
    }
}
