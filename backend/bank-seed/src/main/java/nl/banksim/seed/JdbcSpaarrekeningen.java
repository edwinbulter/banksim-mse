package nl.banksim.seed;

import java.time.LocalDate;
import java.util.Optional;
import java.util.function.Supplier;

import nl.banksim.domain.geld.Bedrag;
import nl.banksim.domain.rekening.Iban;
import org.springframework.jdbc.core.simple.JdbcClient;

/** S8.2: schrijft in het schema van bank-api, als databasegebruiker bank_seed (alleen SELECT en INSERT). */
class JdbcSpaarrekeningen implements Spaarrekeningen {

    private final JdbcClient jdbc;
    private final Supplier<Iban> nieuweIban;

    JdbcSpaarrekeningen(JdbcClient jdbc, Supplier<Iban> nieuweIban) {
        this.jdbc = jdbc;
        this.nieuweIban = nieuweIban;
    }

    @Override
    public Iban open(String gebruikersnaam, Bedrag saldo, Optional<LocalDate> geopendOp) {
        Iban iban = nieuweIban.get();
        int geopend = jdbc.sql("""
                        INSERT INTO rekening (iban, rekeninghouder_id, soort, openingssaldo, geopend_op)
                        SELECT :iban, id, 'spaar', :openingssaldo, :geopendOp FROM rekeninghouder
                        WHERE gebruikersnaam = :gebruikersnaam""")
                .param("iban", iban.toString())
                .param("geopendOp", geopendOp.orElse(null), java.sql.Types.DATE)
                .param("openingssaldo", saldo.waarde())
                .param("gebruikersnaam", gebruikersnaam)
                .update();
        if (geopend == 0) {
            throw new IllegalArgumentException("Geen rekeninghouder met gebruikersnaam " + gebruikersnaam);
        }
        return iban;
    }
}
